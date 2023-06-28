# Generate datalog facts from a scala file

The `examples` directory contains examples that can be compiled and used as an input to
the program.

## Generate TASTy files
The first step is to generate TASTy files from a `.scala` source file using the scala compiler.
Suppose we want to analyze the *pointstofun* example:
```bash
scalac -d examples/pointstofun/ examples/pointstofun/PointsToFun.scala
```

There should be a new `.tasty` file for each class present in the source file.

## Generate the facts from the TASTy file
Now we can run the program to generate the facts (from SBT).
We need to use the `-m` option to indicate the entry point to the program
(typically the main method).

```bash
run -m listlib.Main.main examples/listlib
```

Several options are available:
* `--help`: show program help
* `--print`: display the generated facts
* `--factsonly`: only generate facts, no datalog execution
* `--main`: entry point to the program (should look like `package.Main.main`)
* `--output`: directory where input facts should be saved (optional, creates a CSV file for each relation)