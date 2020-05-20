/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';
import * as utils from './utils';
import setVariableValuesMultiStepInput from './setVariableValuesMultiStepInput';
import { Socket } from 'net';
import { pathToFileURL } from 'url';
import { LanguageClient, LanguageClientOptions, StreamInfo, ResolveCodeLensSignature, ProvideCodeLensesSignature } from 'vscode-languageclient';
import { installGraalVM, installGraalVMComponent, selectInstalledGraalVM } from './graalVMInstall';
import { addNativeImageToPOM } from './graalVMNativeImage';
import { PUBLISH_DECORATIONS_REQUEST, publishDecorations } from './custom_lsp_actions/publishDecorations';


const OPEN_SETTINGS: string = 'Open Settings';
const INSTALL_GRAALVM: string = 'Install GraalVM';
const SELECT_GRAALVM: string = 'Select GraalVM';
const INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT: string = 'Install GraalVM native-image Component';
const START_DEBUG_CONNECT: string = 'Debug connect';
const POLYGLOT: string = "polyglot";
const LSPORT: number = 8123;

let client: LanguageClient | undefined;
let languageServerPID: number = 0;

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.selectGraalVMHome', () => {
		selectInstalledGraalVM(context.globalStoragePath);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installGraalVM', () => {
		installGraalVM(context.globalStoragePath);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installGraalVMComponent', (componentId: string) => {
		installGraalVMComponent(componentId);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.addNativeImageToPOM', () => {
		addNativeImageToPOM();
	}));
	const configurationProvider = new GraalVMConfigurationProvider();
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('graalvm', configurationProvider));
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('node', configurationProvider));
	const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
	if (inProcessServer) {
		context.subscriptions.push(vscode.debug.registerDebugAdapterTrackerFactory('graalvm', new GraalVMDebugAdapterTracker()));
	}
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('graalvm.home')) {
			config();
			stopLanguageServer().then(() => startLanguageServer(context, vscode.workspace.getConfiguration('graalvm').get('home') as string));
		} else if (e.affectsConfiguration('graalvm.languageServer.currentWorkDir') || e.affectsConfiguration('graalvm.languageServer.inProcessServer')) {
			stopLanguageServer().then(() => startLanguageServer(context, vscode.workspace.getConfiguration('graalvm').get('home') as string));
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidChangeTextDocument((params) => {
		console.log(`[${Date.now()}] ${params.document.uri} changed`);
	}));
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (!graalVMHome) {
		vscode.window.showInformationMessage('No path to GraalVM home specified.', SELECT_GRAALVM, INSTALL_GRAALVM, OPEN_SETTINGS).then(value => {
			switch (value) {
				case SELECT_GRAALVM:
					vscode.commands.executeCommand('extension.graalvm.selectGraalVMHome');
					break;
				case INSTALL_GRAALVM:
					vscode.commands.executeCommand('extension.graalvm.installGraalVM');
					break;
				case OPEN_SETTINGS:
					vscode.commands.executeCommand('workbench.action.openSettings');
					break;
			}
		});
	} else {
		config();
		startLanguageServer(context, graalVMHome);
	}
}

export function deactivate(): Thenable<void> {
	return stopLanguageServer();
}

function startLanguageServer(context: vscode.ExtensionContext, graalVMHome: string) {
	const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
	if (inProcessServer) {
		const re = utils.findExecutable(POLYGLOT, graalVMHome);
		if (re) {
			let serverWorkDir: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.currentWorkDir') as string;
			if (!serverWorkDir) {
				serverWorkDir = vscode.workspace.rootPath;
			}
			let delegateServers: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.delegateServers') as string;
			let lspOpt = '--lsp';
			if (delegateServers) {
				lspOpt = '--lsp.Delegates=' + delegateServers;
			}
			const serverProcess = cp.spawn(re, ['--vm.Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n', '--log.lsp.level=ALL', '--jvm', lspOpt, '--experimental-options', '--shell'], { cwd: serverWorkDir });
			if (!serverProcess || !serverProcess.pid) {
				vscode.window.showErrorMessage(`Launching server using command ${re} failed.`);
			} else {
				languageServerPID = serverProcess.pid;
				serverProcess.stdout.once('data', () => {
					vscode.window.showInformationMessage('Click when ready to debug', START_DEBUG_CONNECT).then(() => {
						connectToLanguageServer(context);
					});
					if (serverProcess) {
					    serverProcess.stderr.on('data', data => {
							console.log(data.toString('utf8'));
						});
					}
				});
			}
		} else {
			vscode.window.showErrorMessage('Cannot find runtime ' + POLYGLOT + ' within your GraalVM installation.');
		}
	}
}

function connectToLanguageServer(context: vscode.ExtensionContext) {
	const connection = () => new Promise<StreamInfo>((resolve, reject) => {
		const socket = new Socket();
		socket.once('error', (e) => {
			reject(e);
		});
		socket.connect(LSPORT, '127.0.0.1', () => {
			resolve({
				reader: socket,
				writer: socket
			});
		});
	});
	let clientOptions: LanguageClientOptions = {
		documentSelector: [
			{ scheme: 'file', language: 'javascript' },
			{ scheme: 'file', language: 'sl' },
			{ scheme: 'file', language: 'python' },
			{ scheme: 'file', language: 'r' },
			{ scheme: 'file', language: 'ruby' }
		],
		middleware: {
			resolveCodeLens(this: void, codeLens: vscode.CodeLens, token: vscode.CancellationToken, next: ResolveCodeLensSignature): vscode.ProviderResult<vscode.CodeLens> {
				return next(codeLens, token);
			},
			provideCodeLenses: async function (this: void, document: vscode.TextDocument, token: vscode.CancellationToken, next: ProvideCodeLensesSignature): Promise<vscode.CodeLens[]> {
				const providerResult = next(document, token);
				let codeLenses = undefined as vscode.CodeLens[];
				if (Array.isArray(providerResult as vscode.CodeLens[])) {
					codeLenses = providerResult as vscode.CodeLens[];
				} else if (typeof (providerResult as Thenable<vscode.CodeLens[]>).then === 'function') {
					codeLenses = await providerResult;
				}

				let index = 0;

				context.subscriptions.forEach(disposable => {
					if (disposable instanceof utils.NamedDisposable && disposable.getName().includes('ask-for-user-input')) {
						disposable.dispose();
					}
				});

				codeLenses.forEach(codeLens => {
					const command = codeLens.command;
					const originalCommand = command.command;
					let inputMapping = {};
					if (command.arguments.some(utils.isCommandExpectingUserInputArgument)) {
						inputMapping = command.arguments.find(utils.isCommandExpectingUserInputArgument).inputMapping;
					}
					const replacementCommand = `${originalCommand}__ask-for-user-input__${index}`;
					const replacementCommandHandler = () => {
						const amountOfInputs = Object.keys(inputMapping).length;
						const options: { [key: string]: (context: vscode.ExtensionContext) => Promise<any> } = {
							[`Create example with ${amountOfInputs} variables`]: () => setVariableValuesMultiStepInput(inputMapping),
						};
						const quickPick = vscode.window.createQuickPick();
						quickPick.items = Object.keys(options).map(label => ({ label }));
						quickPick.onDidChangeSelection(selection => {
							if (selection[0]) {
								options[selection[0].label](context)
									.then((inputMapping?: IInputMapping) => vscode.commands.executeCommand(originalCommand, {
										...command.arguments.reduce((object, argument) => {
											return {
												...object,
												...argument,
											};
										}, {}), inputMapping
									}))
									// tslint:disable-next-line: no-console
									.catch(console.error);
							}
						});
						quickPick.onDidHide(() => quickPick.dispose());
						quickPick.show();
					};
					const disposable: vscode.Disposable = vscode.commands.registerCommand(replacementCommand, replacementCommandHandler);
					context.subscriptions.push(new utils.NamedDisposable(disposable, replacementCommand));
					command.command = replacementCommand;
					index++;
				});

				return codeLenses;
			},
		},
	};

	client = new LanguageClient('GraalVM Language Client', connection, clientOptions);
	let prepareStatus = vscode.window.setStatusBarMessage("Graal Language Client: Connecting to GraalLS");
	client.onReady().then(() => {
		prepareStatus.dispose();
		client.onNotification(PUBLISH_DECORATIONS_REQUEST, publishDecorations);
		vscode.window.setStatusBarMessage('GraalLS is ready.', 3000);
	}).catch(() => {
		prepareStatus.dispose();
		vscode.window.setStatusBarMessage('GraalLS failed to initialize.', 3000);
	});
	client.start();
}

function config() {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const javaConfig = vscode.workspace.getConfiguration('java');
		if (javaConfig) {
			const home = javaConfig.inspect('home');
			if (home) {
				javaConfig.update('home', graalVMHome, true);
			}
		}
		const mvnConfig = vscode.workspace.getConfiguration('maven');
		if (mvnConfig) {
			const terminalEnv = javaConfig.inspect('terminal.customEnv');
			if (terminalEnv) {
				mvnConfig.update('terminal.customEnv', [{"environmentVariable": "JAVA_HOME", "value": graalVMHome}], true);
			}
		}
		const executable: string = path.join(graalVMHome, 'bin', 'native-image');
		if (!fs.existsSync(executable)) {
			vscode.window.showInformationMessage('Native-image component is not installed in your GraalVM.', INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT).then(value => {
				switch (value) {
					case INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT:
						vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'native-image');
						break;
				}
			});
		}
	}
}

function stopLanguageServer(): Thenable<void> {
	if (client) {
		return client.stop().then(() => {
			client = undefined;
			if (languageServerPID > 0) {
				terminateLanguageServer();
			}
		});
	}
	if (languageServerPID > 0) {
		terminateLanguageServer();
	}
	return Promise.resolve();
}

function terminateLanguageServer() {
	const groupPID = -languageServerPID;
	try {
		process.kill(groupPID, 'SIGKILL');
	} catch (e) {
		if (e.message === 'kill ESRCH') {
			try {
				process.kill(languageServerPID, 'SIGKILL');
			} catch (e) {}
		}
	}
	languageServerPID = 0;
}

function updatePath(path: string | undefined, graalVMBin: string): string {
	if (!path) {
		return graalVMBin;
	}
	let pathItems = path.split(':');
	let idx = pathItems.indexOf(graalVMBin);
	if (idx < 0) {
		pathItems.unshift(graalVMBin);
	}
	return pathItems.join(':');
}

class GraalVMDebugAdapterTracker implements vscode.DebugAdapterTrackerFactory {

	createDebugAdapterTracker(_session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterTracker> {
		return {
			onDidSendMessage(message: any) {
				if (!client && message.type === 'event') {
					if (message.event === 'output' && message.body.category === 'telemetry' && message.body.output === 'childProcessID') {
						languageServerPID = message.body.data.pid;
					}
					if (message.event === 'initialized') {
						connectToLanguageServer(null);
					}
				}
			}
		};
	}
}

class GraalVMConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
		const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
		if (graalVMHome) {
			config.graalVMHome = graalVMHome;
			const graalVMBin = path.join(graalVMHome, 'bin');
			if (config.env) {
				config.env['PATH'] = updatePath(config.env['PATH'], graalVMBin);
			} else {
				config.env = { 'PATH': graalVMBin };
			}
		}
		if (inProcessServer) {
			stopLanguageServer();
                        let delegateServers: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.delegateServers') as string;
			if (config.runtimeArgs) {
				let idx = config.runtimeArgs.indexOf('--jvm');
				if (idx < 0) {
					config.runtimeArgs.unshift('--jvm');
				}
				idx = config.runtimeArgs.indexOf('--lsp');
				if (idx < 0) {
					config.runtimeArgs.unshift('--lsp');
					if (delegateServers) {
						config.runtimeArgs.unshift('--lsp.Delegates=' + delegateServers);
					}
				}
				idx = config.runtimeArgs.indexOf('--experimental-options');
				if (idx < 0) {
					config.runtimeArgs.unshift('--experimental-options');
				}
			} else {
				if (delegateServers) {
					config.runtimeArgs = ['--jvm', '--lsp.Delegates=' + delegateServers, '--experimental-options'];
				} else {
					config.runtimeArgs = ['--jvm', '--lsp', '--experimental-options'];
				}
			}
		} else if (config.program) {
			vscode.commands.executeCommand('dry_run', pathToFileURL(this.resolveVarRefs(config.program)));
		}
		return config;
	}

	resolveVarRefs(programPath: string): string {
		let re = /\${([\w:]+)}/ig;
		let idx = 0;
		let result = '';
		let match;
		while ((match = re.exec(programPath)) !== null) {
			result += programPath.slice(idx, match.index);
			idx = re.lastIndex;
			switch (match[1]) {
				case 'workspaceRoot':
				case 'workspaceFolder':
					if (vscode.workspace.rootPath) {
						result += vscode.workspace.rootPath;
					}
					break;
				case 'workspaceRootFolderName':
				case 'workspaceFolderBasename':
					if (vscode.workspace.rootPath) {
						result += path.basename(vscode.workspace.rootPath);
					}
					break;
				case 'file':
					if (vscode.window.activeTextEditor) {
						result += vscode.window.activeTextEditor.document.uri.fsPath;
					}
					break;
				case 'relativeFile':
					if (vscode.window.activeTextEditor) {
						let filename = vscode.window.activeTextEditor.document.uri.fsPath;
						result += vscode.workspace.rootPath ? path.normalize(path.relative(vscode.workspace.rootPath, filename)) : filename;
					}
					break;
				case 'relativeFileDirname':
					if (vscode.window.activeTextEditor) {
						let dirname = path.dirname(vscode.window.activeTextEditor.document.uri.fsPath);
						result += vscode.workspace.rootPath ?  path.normalize(path.relative(vscode.workspace.rootPath, dirname)) : dirname;
					}
					break;
				case 'fileBasename':
					if (vscode.window.activeTextEditor) {
						result += path.basename(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'fileBasenameNoExtension':
					if (vscode.window.activeTextEditor) {
						let basename = path.basename(vscode.window.activeTextEditor.document.uri.fsPath);
						result += basename.slice(0, basename.length - path.extname(basename).length);
					}
					break;
				case 'fileDirname':
					if (vscode.window.activeTextEditor) {
						result += path.dirname(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'fileExtname':
					if (vscode.window.activeTextEditor) {
						result += path.extname(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'cwd':
					result += process.cwd;
					break;
				case 'lineNumber':
					if (vscode.window.activeTextEditor) {
						result += vscode.window.activeTextEditor.selection.active.line;
					}
					break;
				case 'selectedText':
					if (vscode.window.activeTextEditor && vscode.window.activeTextEditor.selection) {
						result += vscode.window.activeTextEditor.document.getText(new vscode.Range(vscode.window.activeTextEditor.selection.start, vscode.window.activeTextEditor.selection.end));
					}
					break;
			}
		}
		result += programPath.slice(idx);
		return result;
	}
}
