interface IInputMapping {
    [variableName: string]: string;
}

interface ICommandExpectingUserInputArgument {
    inputMapping: IInputMapping;
}
