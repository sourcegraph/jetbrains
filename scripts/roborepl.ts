/**
 * A REPL for evaluating Rhino JS in the IDE via the IDEA Remote Robot.
 */

const readline = require('readline');
var io = require('java.io');
var InputObjectStream = io.InputObjectStream;
var OutputObjectStream = io.OutputObjectStream;

const port = process.argv[2] || '8082'

async function main() {
    try {
        const response = await fetch(`http://localhost:${port}/hello`)
        const text = await response.text()
        console.log(text)
    } catch (error) {
        console.error('Error:', error)
        console.error('Try running ./gradlew runIdeForUiTests to start the IDE first')
        process.exit(1)
    }

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });

    try {
        while (true) {
            const line = await new Promise(resolve => {
                rl.question('> ', resolve);
            });

            try {
                const response = await fetch(`http://localhost:${port}/js/retrieveAny`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        script: line,
                        runInEdt: true
                    })
                });
                const result = await response.json();
                console.log(result);
                // Read object and return whole info
//                const ios = new InputObjectStream(Buffer.from(result.bytes), true);
//                const obj = ios.readObject();
// // Read object but return value only
                 const in2 = new InputObjectStream(Buffer.from(result.bytes));
                 const obj2 = in2.readObject();
                console.log(obj2);
            } catch (error) {
                console.error('Error:', error);
            }
        }
    } finally {
        rl.close();
    }
}

main()
