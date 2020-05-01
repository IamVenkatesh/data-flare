package qualitychecker.deequ

import java.time.Instant

import com.amazon.deequ.VerificationResult
import qualitychecker.checks.{CheckResult, CheckStatus}
import qualitychecker.{CheckSuiteStatus, ChecksSuiteResult, DeequCheckStatus, QcType}

object DeequHelpers {
  implicit class VerificationResultToQualityCheckResult(verificationResult: VerificationResult) {
    def toCheckSuiteResult(description: String, timestamp: Instant, checkTags: Map[String, String]): ChecksSuiteResult = {
      val checkStatus = verificationResult.status match {
        case com.amazon.deequ.checks.CheckStatus.Success => CheckSuiteStatus.Success
        case com.amazon.deequ.checks.CheckStatus.Warning => CheckSuiteStatus.Warning
        case com.amazon.deequ.checks.CheckStatus.Error => CheckSuiteStatus.Error
      }
      val checkSuiteResultDescription = checkStatus match {
        case qualitychecker.CheckSuiteStatus.Success => "All Deequ checks were successful"
        case qualitychecker.CheckSuiteStatus.Warning => "Deequ checks returned a warning"
        case CheckSuiteStatus.Error => "Deequ checks returned an error"
      }
      val checkResults = verificationResult.checkResults.map{ case (deequCheck, deequCheckResult) =>
        val checkResultDescription = deequCheckResult.status match {
          case com.amazon.deequ.checks.CheckStatus.Success => "Deequ check was successful"
          case com.amazon.deequ.checks.CheckStatus.Warning => "Deequ check produced a warning"
          case com.amazon.deequ.checks.CheckStatus.Error => "Deequ check produced an error"
        }
        CheckResult(deequCheckResult.status.toCheckStatus, checkResultDescription, deequCheck.description)
      }.toSeq
      ChecksSuiteResult( // Do we want to add deequ constraint results to the checks suite result too? It's another level compared to what we have elsewhere. Could refactor to match deequ's way of doing things
        checkStatus,
        description,
        checkSuiteResultDescription,
        checkResults,
        timestamp,
        QcType.DeequQualityCheck,
        checkTags
      )
    }
  }

  implicit class DeequCheckStatusEnricher(checkStatus: DeequCheckStatus) {
    def toCheckStatus = checkStatus match {
      case DeequCheckStatus.Success => CheckStatus.Success
      case DeequCheckStatus.Warning => CheckStatus.Warning
      case DeequCheckStatus.Error => CheckStatus.Error
    }
  }
}
