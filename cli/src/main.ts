/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import * as commander from 'commander';
import * as leven from 'leven';
import { createNamespace } from './create-namespace';
import { verifyPat } from './verify-pat';
import { publish } from './publish';
import { unpublish } from './unpublish';
import { handleError, parseNonNegativeInt } from './util';
import { getExtension } from './get';
import { list } from './list';
import { DEFAULT_SEARCH_SIZE, SORT_KEYS, SORT_ORDERS, search } from './search';
import { show } from './show';
import { verify } from './verify';
import { verifySignature } from './verify-signature';
import login from './login';
import logout from './logout';
import { LIB_VERSION } from './version';

module.exports = function (argv: string[]): void {
    const program = new commander.Command();
    program.usage('<command> [options]')
        .option('-r, --registryUrl <url>', 'Use the registry API at this base URL.')
        .option('-p, --pat <token>', 'Personal access token.')
        .option('--debug', 'Include debug information on error')
        .version(LIB_VERSION, '-V, --version', 'Print the Eclipse Open VSX CLI version');

    const createNamespaceCmd = program.command('create-namespace <name>');
    createNamespaceCmd.description('Create a new namespace')
        .action((name: string) => {
            const { registryUrl, pat } = program.opts();
            createNamespace({ name, registryUrl, pat })
                .catch(handleError(program.debug));
        });

    const verifyTokenCmd = program.command('verify-pat [namespace]');
    verifyTokenCmd.description('Verify that a personal access token can publish to a namespace')
        .action((namespace?: string) => {
            const { registryUrl, pat } = program.opts();
            verifyPat({ namespace, registryUrl, pat })
                .catch(handleError(program.debug));
        });

    const publishCmd = program.command('publish [extension.vsix]');
    publishCmd.description('Publish an extension, packaging it first if necessary.')
        .option('-t, --target <targets...>', 'Target architectures')
        .option('-i, --packagePath <paths...>', 'Publish the provided VSIX packages.')
        .option('--baseContentUrl <url>', 'Prepend all relative links in README.md with this URL.')
        .option('--baseImagesUrl <url>', 'Prepend all relative image links in README.md with this URL.')
        .option('--yarn', 'Use yarn instead of npm while packing extension files.')
        .option('--follow-symlinks', 'Recurse into symlinked directories instead of packing each symlink as a file.')
        .option('--pre-release', 'Mark this package as a pre-release')
        .option('--allow-missing-repository', 'Allow packaging an extension whose package.json has no repository field')
        .option('--no-dependencies', 'Disable dependency detection via npm or yarn')
        .option('--skip-duplicate', 'Fail silently if version already exists on the marketplace')
        .option('--packageVersion <version>', 'Version of the provided VSIX packages.')
        .option('--trusted-publishing', 'Exchange an OIDC ID token for a short-lived publishing token. Enabled automatically when a CI system provides an ID token and no access token is given.')
        .option('--idToken <token>', 'The OIDC ID token to exchange. Only needed on CI systems that provide the token directly, e.g. GitLab CI.')
        .option('--oidcAudience <audience>', 'Audience to request for the OIDC ID token. Defaults to the registry URL.')
        .action((extensionFile: string, { target, packagePath, baseContentUrl, baseImagesUrl, yarn, followSymlinks, preRelease, allowMissingRepository, dependencies, skipDuplicate, packageVersion, trustedPublishing, idToken, oidcAudience }) => {
            if (extensionFile !== undefined && packagePath !== undefined) {
                console.error('\u274c  Please specify either a package file or a package path, but not both.\n');
                publishCmd.help();
            }
            if (extensionFile !== undefined && target !== undefined) {
                console.warn("Ignoring option '--target' for prepackaged extension.");
                target = undefined;
            }
            if (extensionFile !== undefined && baseContentUrl !== undefined)
                console.warn("Ignoring option '--baseContentUrl' for prepackaged extension.");
            if (extensionFile !== undefined && baseImagesUrl !== undefined)
                console.warn("Ignoring option '--baseImagesUrl' for prepackaged extension.");
            if (extensionFile !== undefined && yarn !== undefined)
                console.warn("Ignoring option '--yarn' for prepackaged extension.");
            if (extensionFile !== undefined && followSymlinks !== undefined)
                console.warn("Ignoring option '--follow-symlinks' for prepackaged extension.");
            if (extensionFile !== undefined && packageVersion !== undefined)
                console.warn("Ignoring option '--packageVersion' for prepackaged extension.");
            if (extensionFile !== undefined && allowMissingRepository !== undefined)
                console.warn("Ignoring option '--allow-missing-repository' for prepackaged extension.");
            const { registryUrl, pat } = program.opts();
            publish({ extensionFile, registryUrl, pat, targets: typeof target === 'string' ? [target] : target, packagePath: typeof packagePath === 'string' ? [packagePath] : packagePath, baseContentUrl, baseImagesUrl, yarn, followSymlinks, preRelease, allowMissingRepository, dependencies, skipDuplicate, packageVersion, trustedPublishing, idToken, oidcAudience })
                .then(results => {
                    const reasons = results.filter(result => result.status === 'rejected')
                        .map(rejectedResult => rejectedResult.reason);

                    if (reasons.length > 0) {
                        const message = 'See the documentation for more information:\n'
                            + 'https://github.com/eclipse/openvsx/wiki/Publishing-Extensions';
                        const errorHandler = handleError(program.debug, message, false);
                        for (const reason of reasons) {
                            errorHandler(reason);
                        }

                        process.exit(1);
                    }
                });
        });

    const unpublishCmd = program.command('unpublish [namespace.extension]');
    unpublishCmd.description('Delete an extension or some of its versions from the registry.')
        .option('-v, --versions <versions...>', 'Only delete the given versions.')
        .option('-t, --target <targets...>', 'Only delete the given target architectures of the given versions.')
        .option('-f, --force', 'Skip the confirmation prompt.')
        .action((extensionId: string | undefined, { versions, target, force }) => {
            const { registryUrl, pat } = program.opts();
            unpublish({
                extensionId,
                versions: typeof versions === 'string' ? [versions] : versions,
                targets: typeof target === 'string' ? [target] : target,
                force,
                registryUrl,
                pat
            }).catch(handleError(program.debug));
        });

    const searchCmd = program.command('search [text]');
    searchCmd.description('Search the registry for extensions.')
        .option('-c, --category <category>', 'Only return extensions in this category.')
        .option('-t, --target <target>', 'Only return extensions built for this target architecture.')
        .option(
            '-s, --size <size>',
            `Number of results to return (default ${DEFAULT_SEARCH_SIZE}).`,
            parseNonNegativeInt
        )
        .option('-o, --offset <offset>', 'Index of the first result, for paging.', parseNonNegativeInt)
        .option('--sort-by <key>', `Sort key: ${SORT_KEYS.join(', ')}.`)
        .option('--sort-order <order>', `Sort order: ${SORT_ORDERS.join(', ')}.`)
        .option('--json', 'Print the raw results as JSON.')
        .action((text: string | undefined, { category, target, size, offset, sortBy, sortOrder, json }) => {
            const { registryUrl } = program.opts();
            search({ text, category, target, size, offset, sortBy, sortOrder, json, registryUrl })
                .catch(handleError(program.debug));
        });

    const listCmd = program.command('list <namespace>');
    listCmd.description('List the extensions published in a namespace.')
        .option('--json', 'Print the raw namespace metadata as JSON.')
        .action((namespace: string, { json }) => {
            const { registryUrl } = program.opts();
            list({ namespace, json, registryUrl }).catch(handleError(program.debug));
        });

    const showCmd = program.command('show <namespace.extension[@version]>');
    showCmd.description('Show an extension\'s metadata.')
        .option('-t, --target <target>', 'Only report on the given target architecture.')
        .option('--all-versions', 'List every published version instead of the most recent few.')
        .option('--json', 'Print the raw metadata as JSON.')
        .action((extensionId: string, { target, allVersions, json }) => {
            const { registryUrl } = program.opts();
            show({ extensionId, target, allVersions, json, registryUrl }).catch(handleError(program.debug));
        });

    const getCmd = program.command('get <namespace.extension>');
    getCmd.description('Download an extension or its metadata.')
        .option('-t, --target <target>', 'Target architecture')
        .option('-v, --versionRange <version>', 'Specify an exact version or a version range.')
        .option('-o, --output <path>', 'Save the output in the specified file or directory.')
        .option('--metadata', 'Print the extension\'s metadata instead of downloading it.')
        .action((extensionId: string, { target, versionRange, output, metadata }) => {
            const { registryUrl } = program.opts();
            getExtension({ extensionId, target: target, version: versionRange, registryUrl, output, metadata })
                .catch(handleError(program.debug));
        });

    const verifyCmd = program.command('verify <extension.vsix>');
    verifyCmd.description('Verify a downloaded package\'s signature against the registry\'s public key.')
        .option('-t, --target <target>', 'Target architecture')
        .action((packagePath: string, { target }) => {
            const { registryUrl } = program.opts();
            verify({ packagePath, target, registryUrl })
                .catch(handleError(program.debug));
        });

    const verifySignatureCmd = program.command('verify-signature');
    verifySignatureCmd.description('Verify a signature file against a package and manifest, entirely offline - like `vsce verify-signature`.')
        .requiredOption('-i, --packagePath <path>', 'Path to the .vsix package.')
        .requiredOption('-m, --manifestPath <path>', 'Path to the signature manifest file.')
        .requiredOption('-s, --signaturePath <path>', 'Path to the signature file.')
        .requiredOption('-k, --publicKeyPath <path>', 'Path to the registry\'s public key file.')
        .action(({ packagePath, manifestPath, signaturePath, publicKeyPath }) => {
            verifySignature({ packagePath, manifestPath, signaturePath, publicKeyPath })
                .catch(handleError(program.debug));
        });

    const loginCmd = program.command('login <namespace>');
    loginCmd.description('Adds a namespace to the list of known namespaces')
        .action((namespace: string) => {
            const { registryUrl, pat } = program.opts();
            login({ namespace, registryUrl, pat }).catch(handleError(program.debug));
        });

    const logoutCmd = program.command('logout <namespace>');
    logoutCmd.description('Removes a namespace from the list of known namespaces')
        .action((namespace: string) => {
            logout(namespace).catch(handleError(program.debug));
        });

    program
        .command('*', '', { noHelp: true })
        .action((cmd: commander.Command) => {
            const availableCommands = program.commands.map((c: any) => c._name) as string[];
            const actualCommand = cmd.args[0];
            if (actualCommand) {
                const suggestion = availableCommands.find(c => leven(c, actualCommand) < c.length * 0.4);
                if (suggestion)
                    console.error(`Unknown command '${actualCommand}', did you mean '${suggestion}'?\n`);
                else
                    console.error(`Unknown command '${actualCommand}'.\n`);
            } else {
                console.error('Unknown command.');
            }
            program.help();
        });

    program.parse(argv);

    if (process.argv.length <= 2) {
        program.help();
    }
};
