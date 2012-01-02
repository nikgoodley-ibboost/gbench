package gbench

import gbench.BenchmarkTestUtilities as U

import org.junit.Test

@Benchmark
class ClassBenchmarkTest18 {
    
    def useClassBenchmark() {
        U.simulateOperation()
    }
    @Test void testUseClassBenchmark() {
        U.defaultAssert(U.firstLine(U.callAndGetStdout({useClassBenchmark()})))
    }
}