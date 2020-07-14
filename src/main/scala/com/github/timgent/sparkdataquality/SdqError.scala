package com.github.timgent.sparkdataquality

import com.github.timgent.sparkdataquality.checks.DatasourceDescription
import com.github.timgent.sparkdataquality.checks.DatasourceDescription.SingleDsDescription
import com.github.timgent.sparkdataquality.checkssuite.DescribedDs
import com.github.timgent.sparkdataquality.metrics.MetricDescriptor

sealed trait SdqError {
  def datasourceDescription: Option[DatasourceDescription]
  def msg: String
  def err: Option[Throwable]
}

object SdqError {
  case class MetricCalculationError(dds: DescribedDs, metricDescriptors: Seq[MetricDescriptor], err: Option[Throwable]) extends SdqError {
    import cats.implicits._
    override def datasourceDescription: Option[SingleDsDescription] = Some(SingleDsDescription(dds.description))

    override def msg: String =
      s"""One of the metrics defined on dataset ${dds.description} could not be calculated
         |Metrics used were:
         |- ${metricDescriptors.map(_.toSimpleMetricDescriptor.show).mkString("\n- ")}""".stripMargin
  }
}
