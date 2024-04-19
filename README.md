## Terminus Java

This repo holds a set of tests to be run against the Casper Java SDK.

Points to note are:

- The tests can be run manually via the Terminus project [here](https://github.com/casper-sdks/terminus) 
- The tests are built using Cucumber features

### How to run locally

- Clone the repo and build the test project

  ```bash
  git clone git@github.com:casper-sdks/terminus-java-tests.git
  
  cd terminus-java-tests/script && chmod +x terminus
  
  ./terminus build
  ```

- The default SDK branch and node docker location can be overridden in the terminus init command 

- ```bash
  ./terminus build -b dev -n cctl:latest
  ```

- To view the defaults and other terminus commands, run

- ```bash
  ./terminus help
  ```

- To list the available test features:

- ```bash
  ./terminus list
  ```

- To run the test features:

- ```bash
  ./terminus test
  ```

- To run an individual test feature:

- ```bash
  ./terminus test -f [ feature ]
  # example
  ./terminus test -f deploys.feature
  ```
  
- JUnit test results will be output to /reports

### How to run locally IDE

Alternatively the tests can be run using an IDE

They are developed using JetBrains IntelliJ

