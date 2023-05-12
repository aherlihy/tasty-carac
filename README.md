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
run -p -m Main.main -o <output-path> examples/pointstofun
```

The program creates a CSV file for each type of facts under `<output-path>`.