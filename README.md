# Generate datalog facts from a scala file

The `examples` directory contains examples that can be compiled and used as an input to
the program.

## Generate TASTy files

## Generate the facts from the TASTy file
Now we can run the program to generate the facts (from SBT).
We need to use the `-m` option to indicate the entry point to the program
(typically the main method).

To run with the examples on the classpath use `runExample`:
```bash
runExample -m listlib.Main.main
```

Alternaitively, you can do
```bash
run -m <put your entry point here> <put the class directory containing the tasty files you want to inspect here>
```

Several options are available:
* `--help`: show program help
* `--print`: display the generated facts
* `--factsonly`: only generate facts, no datalog execution
* `--main`: entry point to the program (should look like `package.Main.main`)
* `--output`: directory where input facts should be saved (optional, creates a CSV file for each relation)
