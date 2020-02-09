'use strict';

import { Position, Range, window, TextEditorDecorationType} from 'vscode';
// import NamedDisposable from '../utils/namedDisposable';

export const PUBLISH_DECORATIONS_REQUEST: string = "textDocument/publishDecorations";
const PROBE_DECORATION_TYPE = "PROBE_DECORATION";
const ASSERTION_DECORATION_TYPE = "ASSERTION_DECORATION";
const EXAMPLE_DECORATION_TYPE = "EXAMPLE_DECORATION";

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

const probeDecorationType: TextEditorDecorationType = window.createTextEditorDecorationType({
	after: {
		color: "white",
		backgroundColor: "#4e7ec2",
		margin: "1rem",
	},
});

const assertionDecorationType: TextEditorDecorationType = window.createTextEditorDecorationType({
	after: {
		color: "white",
		margin: "1rem",
	},
});

const exampleDecorationType: TextEditorDecorationType = window.createTextEditorDecorationType({
	after: {
		color: "white",
		backgroundColor: "#636360",
		margin: "1rem",
	},
});

const buildDecorationOption = ({range, decorationText}) => ({
	range,
	renderOptions: {
		after: {
			contentText: `\u202F${decorationText}\u202F`,
		},
	},
});

export function publishDecorations({ uri, decorations }): void {
	console.debug('received new decorations');

	// replace URL encoded spaces
	const fileName: string = uri.split('file://')[1].replace(/%20/g, ' ');
	const openEditor = window.visibleTextEditors.filter(editor => editor.document.fileName === fileName)[0];

	decorations = decorations.map(
		({range: rangeParams, decorationType, decorationText}) => ({range: parseRangeFromGraal(rangeParams), decorationType, decorationText})
	);
	decorations.forEach(decoration => console.debug(
		decoration.decorationText,
		decoration.decorationType,
		decoration.range.start.line,
		decoration.range.start.character,
		decoration.range.end.line,
		decoration.range.end.character
	));

	// combine decorations if there are multiple decorations per line
	decorations = decorations.reduce((decorationsAccumulator, newDecoration) => {
		let existingDecorationWithSameRange = decorationsAccumulator.find(existingDecoration => existingDecoration.range.isEqual(newDecoration.range));

		if (existingDecorationWithSameRange) {
			existingDecorationWithSameRange.decorationText += `, ${newDecoration.decorationText}`;
		} else {
			decorationsAccumulator.push(newDecoration);
		}
		return decorationsAccumulator;
	}, []);

	const probeDecorations = decorations.filter(decoration => decoration.decorationType === PROBE_DECORATION_TYPE);
	const assertionDecorations = decorations.filter(decoration => decoration.decorationType === ASSERTION_DECORATION_TYPE);
	const exampleDecorations = decorations.filter(decoration => decoration.decorationType === EXAMPLE_DECORATION_TYPE);

	openEditor.setDecorations(
		probeDecorationType,
		probeDecorations.map(buildDecorationOption)
	);

	openEditor.setDecorations(
		assertionDecorationType,
		assertionDecorations.map(buildDecorationOption)
	);

	openEditor.setDecorations(
		exampleDecorationType,
		exampleDecorations.map(buildDecorationOption)
	);
}
