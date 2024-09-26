/**
 * Checks whether a given JetBrains release contains a specific Cody commit.
 */

const {execSync} = require("child_process");

console.log(`
This script checks whether a given JetBrains release contains a specific Cody commit.
To fetch version git tags from origin, run:
  git fetch origin 'refs/tags/v*:refs/tags/v*'
`);

// Check if CODY_DIR is set
const CODY_DIR = process.env.CODY_DIR;
if (CODY_DIR) {
    console.log(`Using sourcegraph/cody repo in ${CODY_DIR}`)
} else {
    console.error('Error: CODY_DIR environment variable is not set.');
    process.exit(1);
}

// Check if targetCommit is provided as a command-line argument
const targetCommit = process.argv[2];
if (!targetCommit) {
    console.error('Error: specify a commit to search for as a command-line argument.');
    process.exit(1);
}

// Step 2: Enumerate matching tags and sort them in reverse order
let tags: string[];
try {
    tags = execSync('git tag -l "v[0-9]*.[0-9]*.[0-9]*"')
        .toString()
        .trim()
        .split('\n')
        .sort((a: string, b: string) => {
            const parseVersion = (version: string) => {
                const [main, modifier] = version.slice(1).split('-');
                const [major, minor, patch] = main.split('.').map(Number);
                return { major, minor, patch, modifier };
            };

            const aVersion = parseVersion(a);
            const bVersion = parseVersion(b);

            if (aVersion.major !== bVersion.major) {
                return bVersion.major - aVersion.major;
            }
            if (aVersion.minor !== bVersion.minor) {
                return bVersion.minor - aVersion.minor;
            }
            if (aVersion.patch !== bVersion.patch) {
                return bVersion.patch - aVersion.patch;
            }
            if (aVersion.modifier && bVersion.modifier) {
                return aVersion.modifier.localeCompare(bVersion.modifier);
            }
            return aVersion.modifier ? -1 : 1;
        });
} catch (error: any) {
    console.error(`Error fetching tags: ${error?.message}`);
    process.exit(1);
}

// Step 3: Extract cody.commit from gradle.properties for each tag
tags.forEach(tag => {
    let codyCommit;
    try {
        const gradleProperties = execSync(`git show ${tag}:gradle.properties`).toString();
        const match = gradleProperties.match(/^cody\.commit=(.*)$/m);
        if (match) {
            codyCommit = match[1];
        } else {
            console.log(`${tag}: ? No cody.commit found`);
            return;
        }
    } catch (error) {
        console.log(`${tag}: ? Error reading gradle.properties`);
        return;
    }

    // Step 4: Fetch the relevant commit in the Cody repository
    try {
        execSync(`git -C ${CODY_DIR} fetch origin ${codyCommit}`, {stdio: 'ignore'});
    } catch (error) {
        console.log(`${tag}: ? Error fetching commit ${codyCommit}`);
        return;
    }

    // Step 5: Determine if the target commit is in the history of codyCommit
    try {
        execSync(`git -C ${CODY_DIR} merge-base --is-ancestor ${targetCommit} ${codyCommit}`);
        console.log(`${tag}: ✓`);
    } catch (error: any) {
        if (error.status === 1) {
            // Exit code 1 means the target commit is not an ancestor
            console.log(`${tag}: ✗`);
        } else {
            // Any other error
            console.log(`${tag}: ? Error checking merge-base`);
        }
    }
});
