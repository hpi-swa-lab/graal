'use strict';

import { DecorationOptions, Position, Range, window, TextEditorDecorationType} from 'vscode';
// import NamedDisposable from '../utils/namedDisposable';

export const PUBLISH_DECORATIONS_REQUEST: string = "custom/publishDecorations";

const parseRangeFromGraal = ({
	start: {
		line: startLine,
		character: startCharacter
	},
	end: {
		line: endLine,
		character: endCharacter
	}
}): Range => new Range(
	// VS Code uses zero-indexing for line numbers while Graal uses one-indexing...
	new Position(startLine - 1, startCharacter),
	new Position(endLine - 1, endCharacter)
);

const explicitDefaultDecorationType: TextEditorDecorationType = window.createTextEditorDecorationType({
	after: {
		color: "white",
		backgroundColor: "#4e7ec2",
		margin: "1rem",
	},
});

function explicitDecorationStyleForType(type: string): object {
	const styleForType = {
		"probeResult": {
			backgroundColor: "#4e7ec2"
		},
		"exampleResult": {
			backgroundColor: "#636360"
		}
	};

	return styleForType[type] || {};
}

export function publishDecorations({ uri, decorations }): void {
	console.debug('received new decorations');

	// replace URL encoded spaces
	const fileName: string = uri.split('file://')[1].replace(/%20/g, ' ');
	const openEditor = window.visibleTextEditors.filter(editor => editor.document.fileName === fileName)[0];

	decorations = decorations.map(
		({range: rangeParams, decorationText, type}) => ({range: parseRangeFromGraal(rangeParams), decorationText, type})
	);
	decorations.forEach(decoration => console.debug(
		decoration.decorationText,
		decoration.range.start.line,
		decoration.range.start.character,
		decoration.range.end.line,
		decoration.range.end.character
	));

	decorations = decorations.reduce((decorationsAccumulator, newDecoration) => {
		let existingDecorationWithSameRange = decorationsAccumulator.find(existingDecoration => existingDecoration.range.isEqual(newDecoration.range));

		if (existingDecorationWithSameRange) {
			existingDecorationWithSameRange.decorationText += `, ${newDecoration.decorationText}`;
		} else {
			existingDecorationWithSameRange = newDecoration;
			decorationsAccumulator.push(existingDecorationWithSameRange);
		}
		return decorationsAccumulator;
	}, []);

	const decorationOptionsArray: DecorationOptions[] = decorations.map(({range, decorationText, type}) => ({
		range,
		renderOptions: {
			after: {
				...explicitDecorationStyleForType(type),
				contentText: ` ${decorationText} `
			}
		}
	}));

	openEditor.setDecorations(
		explicitDefaultDecorationType,
		decorationOptionsArray
	);
}
