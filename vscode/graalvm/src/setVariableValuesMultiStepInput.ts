import { QuickPickItem } from 'vscode';
import { toJsonWithSpaces } from './utils';
import { MultiStepInput } from './utils';

interface ISetVariableValueQuickPickItem<T> extends QuickPickItem {
    variableName: string;
    variableValue: T;
    variableType: string;
}

/**
 * A multi-step input for setting the values of variables (defined by their name and type in a
 * corresponding InputMapping object) using a QuickPick based multi-step input.
 *
 * Adapted from https://github.com/microsoft/vscode-extension-samples/tree/master/quickinput-sample
 */
export default async function setVariableValuesMultiStepInput(
    inputMapping: IInputMapping
): Promise<IExampleMapping> {
    const FINISH_STRING = '› Finish';

    const variableQuickPicks: Array<ISetVariableValueQuickPickItem<
        any
    >> = Object.entries(inputMapping).map(
        ([variableName, variableType]) => {
            return {
                label: `${variableName} (${variableType})`,
                variableName,
                variableType,
                variableValue: undefined,
            };
        }
    );

    interface IState {
        title: string;
        step: number;
        totalSteps: number;
        selectedVariableQuickPick: ISetVariableValueQuickPickItem<any>;
        variableQuickPicks: Array<ISetVariableValueQuickPickItem<any>>;
        exampleName: string;
        probeMode: string;
        finished: boolean;
    }

    async function collectInputs() {
        const finalState = {
            finished: false,
            variableQuickPicks,
        } as Partial<IState>;
        await MultiStepInput.run(input => inputExampleName(input, finalState));
        return finalState as IState;
    }

    const title = 'Create an example for a function';

    async function inputExampleName(input: MultiStepInput, currentState: Partial<IState>) {
        currentState.exampleName = await input.showInputBox({
            prompt: `Choose a name for the example`,
            shouldResume,
            step: 1,
            title,
            totalSteps: 4,
            validate: validateExampleName,
            value: typeof currentState.exampleName === 'string' ? currentState.exampleName : '',
        });
        return (input: MultiStepInput) => pickProbeMode(input, currentState);
    }

    const probeModes: QuickPickItem[] = [
        ['default', 'Only return values and explicit probes'], ['all', 'All statements'], ['off', 'Disable probing']
    ].map(entry => ({ label: entry[0], detail: entry[1] }));

    async function pickProbeMode(input: MultiStepInput, currentState: Partial<IState>) {
		const pick = await input.showQuickPick({
			title,
			step: 2,
			totalSteps: 4,
			placeholder: 'Pick a probe mode',
			items: probeModes,
			activeItem: typeof currentState.probeMode !== 'string' ? currentState.probeMode : undefined,
			shouldResume: shouldResume
        });
        currentState.probeMode = pick.label;
        if (currentState.variableQuickPicks.length === 0) {
            return undefined;
        }
		return (input: MultiStepInput) => pickVariable(input, currentState);
    }

    async function pickVariable(input: MultiStepInput, currentState: Partial<IState>) {
        const pick = await input.showQuickPick({
            activeItem: currentState.selectedVariableQuickPick
                ? currentState.selectedVariableQuickPick
                : undefined,
            items: currentState.finished
                ? [
                    {
                        label: FINISH_STRING,
                    },
                    ...currentState.variableQuickPicks,
                ]
                : currentState.variableQuickPicks,
            placeholder: 'Select a variable to set its value',
            shouldResume,
            step: 3,
            title,
            totalSteps: 3,
        });
        if (pick.label === FINISH_STRING) {
            return undefined;
        }
        currentState.selectedVariableQuickPick = pick as ISetVariableValueQuickPickItem<
            any
        >;
        return (nextInput: MultiStepInput) => inputValue(nextInput, currentState);
    }

    async function inputValue(input: MultiStepInput, currentState: Partial<IState>) {
        const newValue = await input.showInputBox({
            prompt: `Choose a value for the selected variable ${currentState.selectedVariableQuickPick.variableName} (${currentState.selectedVariableQuickPick.variableType})`,
            shouldResume,
            step: 4,
            title,
            totalSteps: 4,
            validate: (value: string) =>
                validateVariableType(
                    value,
                    currentState.selectedVariableQuickPick.variableType
                ),
            value:
                toJsonWithSpaces(
                    currentState.selectedVariableQuickPick.variableValue
                ) || '',
        });
        const newValueJson = JSON.parse(newValue) as object;
        const quickPickToUpdate = currentState.variableQuickPicks.find(
            quickPick =>
                quickPick.variableName ===
                currentState.selectedVariableQuickPick.variableName
        );
        quickPickToUpdate.variableValue = newValueJson;
        quickPickToUpdate.label = `${quickPickToUpdate.variableName} (${
            quickPickToUpdate.variableType
            }): ${toJsonWithSpaces(newValueJson)}`;
        if (
            currentState.variableQuickPicks.every(
                quickPick => quickPick.variableValue !== undefined
            )
        ) {
            currentState.finished = true;
        }
        return (nextInput: MultiStepInput) => pickVariable(nextInput, currentState);
    }

    function shouldResume() {
        // Could show a notification with the option to resume.
        return new Promise<boolean>(() => undefined);
    }

    async function validateExampleName(name: string) {
		// TODO: check if name is unique
		return undefined;
	}

    async function validateVariableType(value: string, type: string) {
        if (type === 'any') {
            try {
                JSON.parse(value);
                return undefined;
            } catch (error) {
                return `The entered value ${value} is not a valid JSON object (type ${type})`;
            }
        }
        if (type.startsWith('Array')) {
            try {
                const parts = JSON.parse(value) as any[];
                const containedType = type
                    .replace('Array<', '')
                    .replace(new RegExp('>$'), '');
                const subValidations = await Promise.all(
                    parts.map(
                        async part =>
                            await validateVariableType(
                                JSON.stringify(part),
                                containedType
                            )
                    )
                );
                const failedValidations = subValidations.filter(
                    validation => !!validation
                );
                if (failedValidations.length === 0) {
                    return undefined;
                }
                return `The entered value ${value} is not of type ${type}`;
            } catch (error) {
                return `The entered value ${value} is not of type ${type}`;
            }
        }
        if (value === '') {
            return `Please enter a value for the required type ${type}`;
        }
        if (type === 'String') {
            try {
                if (typeof JSON.parse(value) !== 'string') {
                    return `The entered value ${value} is not of type ${type}`;
                }
                return undefined;
            } catch (error) {
                return `The entered value ${value} is not of type ${type}`;
            }
        } else if (type === 'Number') {
            try {
                if (typeof JSON.parse(value) !== 'number') {
                    return `The entered value ${value} is not of type ${type}`;
                }
                return undefined;
            } catch (error) {
                return `The entered value ${value} is not of type ${type}`;
            }
        }
        return `Unsupported type for validation: ${type}`;
    }

    const state = await collectInputs();
    const variableNameValueMapping = {} as IInputMapping;
    state.variableQuickPicks.forEach(quickPick => {
        variableNameValueMapping[quickPick.variableName] =
            quickPick.variableValue;
    });

    const exampleMapping = {} as IExampleMapping;
    exampleMapping.exampleName = state.exampleName;
    exampleMapping.probeMode = state.probeMode;
    exampleMapping.variables = variableNameValueMapping;

    return exampleMapping;
}
