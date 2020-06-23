[![javadoc](https://javadoc.io/badge2/com.github.timgent/spark-data-quality_2.11/javadoc.svg)](https://javadoc.io/doc/com.github.timgent/spark-data-quality_2.11)
![](https://github.com/timgent/spark-data-quality/workflows/Scala%20CI/badge.svg)

# Spark Data Quality
A data quality library build with spark and deequ, to give you ultimate flexibility and power in ensuring your data
is of high quality.

# Key Concepts
* A `ChecksSuite` is a suite of checks that perform a given types of checks
* A checks suite is made up of a number of `QCCheck`s. QCChecks define a check to do on the data. The types of `QCCheck`s
you can perform aligns with the `ChecksSuite` above:
    * `SingleMetricBasedCheck` - a metric based check performed on a single dataset. Metric based checks are designed
    to all be calculated in one pass over the dataset, even if they are part of different checks. This makes them more
    efficient than other check types (deequ uses a similar mechanism and so is also efficient)
    * `DualDatasetMetricChecks` - a metric based check performed on a pair of datasets
    * `DeequQCCheck` - wrapper for [deequ](https://github.com/awslabs/deequ/tree/master/src/main/scala/com/amazon/deequ)'s `Check` type
    * `SingleDatasetCheck` - a check performed on a single dataset
    * `DatasetComparisonCheck` - a check performed across 2 datasets
    * `ArbitraryCheck` - a completely arbitrary check

# Getting started
Add the following to your dependencies:
```
libraryDependencies += "com.github.timgent" % "spark-data-quality_2.11" % "x.x.x"
```
For other build systems like maven, and to check the latest version go to 
https://search.maven.org/artifact/com.github.timgent/spark-data-quality_2.11

You can [find the javadocs here](https://www.javadoc.io/doc/com.github.timgent/spark-data-quality_2.11/latest/index.html#package)

## ChecksSuite
A ChecksSuite lets you perform a number of checks under a single umbrella. To get started defining a suite of checks
checkout the [example](src/main/scala/com/github/timgent/sparkdataquality/examples). All possible checks are documented
in the API docs and codebase - just look for anything that implements the QCCheck trait. The tests are also a great
resource to see example usage.

### Running metric based checks without deequ
We've built in some metric based checks directly to this library due to some limitations with deequ. In time we hope
to cover the majority of functionality deequ provides. Currently with the build in metrics checks you can:

* Efficiently calculate a few types of metrics on your datasets
* Perform checks on the values of those metrics, either checking they are within a certain range on a single dataset,
or comparing the metric values between datasets
* Store metrics using a MetricsPersister. Currently there is an InMemoryMetricsPersister or an 
ElasticSearchMetricsPersister. The advantage of the ElasticSearch persister is that once your metrics are in 
ElasticSearch you can easily use Kibana to graph them over time and set up dashboards to track your metrics

## Published with SBT Sonatype
https://github.com/xerial/sbt-sonatype

