interface IInputMapping {
    [variableName: string]: string;
}

interface IExampleMapping {
    exampleName: string;
    probeMode: string;
    variables: IInputMapping;
}

interface ICommandExpectingUserInputArgument {
    inputMapping: IInputMapping;
}
