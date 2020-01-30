/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as path from 'path';
import * as fs from 'fs';
import { Disposable, QuickInput, QuickInputButton, QuickInputButtons, QuickPickItem, window } from 'vscode';

export function random(low: number, high: number): number {
    return Math.floor(Math.random() * (high - low) + low);
}

export function findExecutable(program: string, graalVMHome: string | undefined): string | undefined {
    if (graalVMHome) {
        let executablePath = path.join(graalVMHome, 'bin', program);
        if (fs.existsSync(executablePath)) {
            return executablePath;
        }
    }
    return undefined;
}

export function isSymlinked(dirPath: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
        fs.lstat(dirPath, (err, stats) => {
            if (err) {
                reject(err);
            }
            if (stats.isSymbolicLink()) {
                resolve(true);
            } else {
                const parent = path.dirname(dirPath);
                if (parent === dirPath) {
                    resolve(false);
                } else {
                    resolve(isSymlinked(parent));
                }
            }
        });
    });
}

export class NamedDisposable {
	private name: string;
	private disposable: Disposable;

	constructor(disposable: Disposable, name: string) {
		this.disposable = disposable;
		this.name = name;
	}

	public setName(name: string) {
		this.name = name;
	}

	public getName() {
		return this.name;
	}

	public dispose() {
		this.disposable.dispose();
	}
}

export function isCommandExpectingUserInputArgument(argument: any): argument is ICommandExpectingUserInputArgument {
    return argument.inputMapping !== undefined;
}

/**
 * Converts an object to its stringified JSON representation while applying
 * the options optionally defined via a ToJsonWithSpacesOptions object.
 *
 * Taken from https://stackoverflow.com/a/57467694
 */
export function toJsonWithSpaces(obj: object, options?: Partial<ToJsonWithSpacesOptions>) {
    options = Object.assign({}, new ToJsonWithSpacesOptions(), options);

    try {
        let result = JSON.stringify(obj, null, 1); // stringify, with line-breaks and indents
        result = result.replace(/^ +/gm, " "); // remove all but the first space for each line
        result = result.replace(/\n/g, ""); // remove line-breaks
        if (!options.insideObjectBraces) { result = result.replace(/{ /g, "{").replace(/ }/g, "}"); }
        if (!options.insideArrayBrackets) { result = result.replace(/\[ /g, "[").replace(/ \]/g, "]"); }
        if (!options.betweenPropsOrItems) { result = result.replace(/, /g, ","); }
        if (!options.betweenPropNameAndValue) { result = result.replace(/": /g, `":`); }
        return result;
    } catch (error) {
        return undefined;
    }
}

export class ToJsonWithSpacesOptions {
    public insideObjectBraces = false;
    public insideArrayBrackets = false;
    public betweenPropsOrItems = true;
    public betweenPropNameAndValue = true;
}

/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

// -------------------------------------------------------
// Helper code that wraps the API for the multi-step case.
// -------------------------------------------------------


class InputFlowAction {
    public static back = new InputFlowAction();
    public static cancel = new InputFlowAction();
    public static resume = new InputFlowAction();
    private constructor() { }
}

type InputStep = (input: MultiStepInput) => Thenable<InputStep | void>;

interface IQuickPickParameters<T extends QuickPickItem> {
    title: string;
    step: number;
    totalSteps: number;
    items: T[];
    activeItem?: T;
    placeholder: string;
    buttons?: QuickInputButton[];
    shouldResume: () => Thenable<boolean>;
}

interface IInputBoxParameters {
    title: string;
    step: number;
    totalSteps: number;
    value: string;
    prompt: string;
    validate: (value: string) => Promise<string | undefined>;
    buttons?: QuickInputButton[];
    shouldResume: () => Thenable<boolean>;
}

// tslint:disable-next-line: max-classes-per-file
export class MultiStepInput {

    public static async run(start: InputStep) {
        const input = new MultiStepInput();
        return input.stepThrough(start);
    }

    private current?: QuickInput;
    private steps: InputStep[] = [];

    public async showQuickPick<T extends QuickPickItem, P extends IQuickPickParameters<T>>({ title, step, totalSteps, items, activeItem, placeholder, buttons, shouldResume }: P) {
        const disposables: Disposable[] = [];
        try {
            return await new Promise<T | (P extends { buttons: Array<infer I> } ? I : never)>((resolve, reject) => {
                const input = window.createQuickPick<T>();
                input.title = title;
                input.step = step;
                input.totalSteps = totalSteps;
                input.placeholder = placeholder;
                input.items = items;
                if (activeItem) {
                    input.activeItems = [activeItem];
                }
                input.buttons = [
                    ...(this.steps.length > 1 ? [QuickInputButtons.Back] : []),
                    ...(buttons || [])
                ];
                disposables.push(
                    input.onDidTriggerButton(item => {
                        if (item === QuickInputButtons.Back) {
                            reject(InputFlowAction.back);
                        } else {
                            resolve(item as any);
                        }
                    }),
                    input.onDidChangeSelection(selectedItems => resolve(selectedItems[0])),
                    input.onDidHide(() => {
                        (async () => {
                            reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
                        })()
                            .catch(reject);
                    })
                );
                if (this.current) {
                    this.current.dispose();
                }
                this.current = input;
                this.current.show();
            });
        } finally {
            disposables.forEach(d => d.dispose());
        }
    }

    public async showInputBox<P extends IInputBoxParameters>({ title, step, totalSteps, value, prompt, validate, buttons, shouldResume }: P) {
        const disposables: Disposable[] = [];
        try {
            return await new Promise<string | (P extends { buttons: Array<infer I> } ? I : never)>((resolve, reject) => {
                const input = window.createInputBox();
                input.title = title;
                input.step = step;
                input.totalSteps = totalSteps;
                input.value = value || '';
                input.prompt = prompt;
                input.buttons = [
                    ...(this.steps.length > 1 ? [QuickInputButtons.Back] : []),
                    ...(buttons || [])
                ];
                let validating = validate('');
                disposables.push(
                    input.onDidTriggerButton(item => {
                        if (item === QuickInputButtons.Back) {
                            reject(InputFlowAction.back);
                        } else {
                            resolve(item as any);
                        }
                    }),
                    input.onDidAccept(async () => {
                        const newValue = input.value;
                        input.enabled = false;
                        input.busy = true;
                        if (!(await validate(newValue))) {
                            resolve(newValue);
                        }
                        input.enabled = true;
                        input.busy = false;
                    }),
                    input.onDidChangeValue(async text => {
                        const current = validate(text);
                        validating = current;
                        const validationMessage = await current;
                        if (current === validating) {
                            input.validationMessage = validationMessage;
                        }
                    }),
                    input.onDidHide(() => {
                        (async () => {
                            reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
                        })()
                            .catch(reject);
                    })
                );
                if (this.current) {
                    this.current.dispose();
                }
                this.current = input;
                this.current.show();
            });
        } finally {
            disposables.forEach(d => d.dispose());
        }
    }

    private async stepThrough(start: InputStep) {
        let step: InputStep | void = start;
        while (step) {
            this.steps.push(step);
            if (this.current) {
                this.current.enabled = false;
                this.current.busy = true;
            }
            try {
                step = await step(this);
            } catch (err) {
                if (err === InputFlowAction.back) {
                    this.steps.pop();
                    step = this.steps.pop();
                } else if (err === InputFlowAction.resume) {
                    step = this.steps.pop();
                } else if (err === InputFlowAction.cancel) {
                    step = undefined;
                } else {
                    throw err;
                }
            }
        }
        if (this.current) {
            this.current.dispose();
        }
    }
}
