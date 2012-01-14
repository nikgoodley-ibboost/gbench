/*
 * Copyright 2011 Nagai Masato
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gbench

import java.lang.management.ManagementFactory

import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener

/**
 * A builder for benchmarking.
 * <p>For example, you can benchmark character concatenation like the 
 * following:</p>
 * <pre><code>
 * def benchmarker = new BenchmarkBuilder()
 * def benchmarks = benchmarker.run repeat: 10000, {
 *     def chars = ['g', 'r', 'o', 'o', 'v', 'y']
 *     concat {
 *         def s = ''
 *         for(c in chars){
 *             s.concat c
 *         }
 *     }
 *     '+=' {
 *         def s = ''
 *         for (c in chars) {
 *             s += c
 *         }
 *     }
 *     stringbuilder {
 *         def sb = new StringBuilder()
 *         for(c in chars){
 *             sb << c
 *         }
 *         sb.toString()
 *     }
 *     join {
 *         chars.join()
 *     }
 * }
 * benchmarks.sort().prettyPrint()
 * <code></pre>
 * then output will be like:
 * <pre>
 *                     user      system         cpu         real
 *
 * join            46800300    15600100    62400400     91680789
 * stringbuilder   62400400    15600100    78000500    101281757
 * +=              62400400    15600100    78000500    121649445
 * concat          46800300    31200200    78000500    129421409
 * </pre>
 * 
 * <table border="1">
 * <caption>System Properties</caption>
 * <tr><th>Key</th><th>Value</th><th>Meaning</th>
 * <tr><td>"gbench.cputime"</td><td>"on","off"</td><td>Enables measuring CPU time. The default value is "on".</td></tr>
 * <tr><td>"gbench.trace"</td><td>"on","off"</td><td>Enables tracing in the builder to understand low-level working. The default value is "off".</td></tr>
 * </table>
 * <p/>
 *
 * @author Nagai Masato
 *
 */
class BenchmarkBuilder {
    
    public static int AUTO = -1;

    Benchmarks benchmarks
    int repeat
    int idle
    boolean average
    boolean trim
    boolean traceEnabled
    boolean cpuTimeEnabled
    
    
    BenchmarkBuilder() {
        traceEnabled = BenchmarkUtilities.isTraceEnabled()
        cpuTimeEnabled = BenchmarkUtilities.isCpuTimeEnabled()
    }

    /**
     * Gets benchmarks.
     * 
     * @param options
     * <ul>
     * <li>average: if <code>true</code>, gets average instead of sum. the 
     *              default value is <code>false</code> (gets sum).</li>
     * <li>repeat:  times to execute each code block. the default value is 
     *              <code>1</code>.</li>
     * <li>idle:    times to execute each code block before starting to 
     *              benchmark. This option is useful to reduce effects of 
     *              overhead. the default value is {@link AUTO}.</li>
     * <li>trim:    if <code>true</code>, removes the highest and the lowest 
     *              benchmarks. the default value is <code>false</code>.</li>
     * </ul>
     * @param clos a closure to add code blocks for benchmarking.
     * @return a list of benchmarks
     */
    def run(Map options=[:], Closure clos) {
        benchmarks = new Benchmarks()
        repeat = options.times ?: /* for backward compatibility */  
                    options.repeat ?: 1
        idle = options.idle ?: AUTO
        average = options.average ?: false
        if (options.trim) {
            trim = true
            if (repeat < 3) {
                // cannot trim in case of lack of times
                trim = false
            }
        }
        clos.resolveStrategy = Closure.DELEGATE_FIRST
        clos.delegate = this
        clos()
        return benchmarks
    }
   
    /**
     * Gets sum of benchmarks. This method behaves the same as 
     * <code>run() or run(average: false)</code>
     * 
     * @param options
     * <ul>
     * <li>repeat:  times to execute each code block. the default value is 
     *              <code>1</code>.</li>
     * <li>idle:    times to execute each code block before starting to 
     *              benchmark. This option is useful to reduce effects of 
     *              overhead. the default value is {@link AUTO}.</li>
     * <li>trim:    if <code>true</code>, removes the highest and the lowest 
     *              benchmarks. the default value is <code>false</code>.</li>
     * </ul>
     * @param clos a closure to add code blocks for benchmarking
     * @return a list of benchmarks
     */
    def sum(Map options=[:], Closure clos) {
        run(options, clos)    
        return benchmarks
    }

    /**
     * Gets average of benchmarks. This method behaves the same as 
     * <code>run(average: true)</code>
     * 
     * @param options
     * <ul>
     * <li>repeat:  times to execute each code block. the default value is 
     *              <code>1</code>.</li>
     * <li>idle:    times to execute each code block before starting to 
     *              benchmark. This option is useful to reduce effects of 
     *              overhead. the default value is {@link AUTO}.</li>
     * <li>trim:    if <code>true</code>, removes the highest and the lowest 
     *              benchmarks. the default value is <code>false</code>.</li>
     * </ul>
     * @param clos a closure to add code blocks for benchmarking
     * @return a list of benchmarks
     */
    def average(Map options=[:], Closure clos) {
        options = new HashMap(options)
        options.average = true
        run(options, clos)    
        return benchmarks
    }
    
    /**
     * Adds a code block as a benchmark target.
     * 
     * @deprecated Use the following alternate syntax instead: 
     *               <code>label { code }</code>
     *               
     * @param label the label of the code block.
     * @param clos a code block.
     */
    def with(String label, Closure clos) {
        benchmarks << measure(label, clos)
    }
    
    /**
     * @deprecated Use <code>benchmarks.each{}</code> instead.
     * @param clos
     */
    def each(Closure clos) {
        benchmarks.each(clos)
        return this
    }

    /**
     * @deprecated Use <code>benchmarks.sort()</code> instead.    
     */
    def sort() {
        benchmarks.sort{ it.time.real }
        return this
    }
    
    String toString() {
        def writer = new StringWriter()
        benchmarks.prettyPrint(new PrintWriter(writer))
        return writer.toString()
    } 
   
    def invokeMethod(String name, Object args) {
        if (args && args.size() == 1) {
            with(name, args[0])
        }
    }
   
    private def cleanHeap() {
        if (traceEnabled) {
            println "[BM] **** heap cleaning started. ****"
        }
        def mMxBean = ManagementFactory.memoryMXBean
        def bHeapUsage = mMxBean.heapMemoryUsage.used
        while (true) {
            System.gc()
            
            // try to gc objects that override finalize() method.
            System.runFinalization()
            System.gc()
            
            def aHeapUsage = mMxBean.heapMemoryUsage.used
            if (bHeapUsage > aHeapUsage) {
                bHeapUsage = aHeapUsage
                continue
            }
            break
        }
        if (traceEnabled) {;
            println "[BM] **** heap cleaning finished. ****"
        }
    }
    
    private def measure(label, Closure clos) {
        cleanHeap()
        def reals = []
        def cpus, systems, users
        def thMxBean
        def clMxBean = ManagementFactory.classLoadingMXBean
        def clear
        if (cpuTimeEnabled) {
            cpus = []
            systems = []
            users = []
            thMxBean = ManagementFactory.threadMXBean 
            clear = {
                users.clear()
                cpus.clear()
                systems.clear()
                reals.clear() 
            }
        } else {
            cpus = systems = users = [0].asImmutable() 
            clear = {
                reals.clear() 
            }
        }
        
        def rest = repeat
        def restOfIdle = AUTO == idle ? 1 : idle
        for (def n = 1; rest > 0; n++) {
            def bLoadedClasses = clMxBean.totalLoadedClassCount
            def bUnloadedClasses = clMxBean.unloadedClassCount
            
            def bReal = System.nanoTime()
            def bCpu
            def bUser
            if (cpuTimeEnabled) {
                bCpu = thMxBean.currentThreadCpuTime
                bUser = thMxBean.currentThreadUserTime
            }
            clos()
            def user = 0L
            def cpu = 0L
            def system = 0L
            def real = System.nanoTime() - bReal
            if (cpuTimeEnabled) {
                user = thMxBean.currentThreadUserTime - bUser
                cpu = thMxBean.currentThreadCpuTime - bCpu
                system = cpu - user
            }
            
            if (traceEnabled) {
                println "[BM] ${label}: n=${n},user=${user},system=${system},cpu=${cpu},real=${real}"
            }
            
            def countable = true
            def warmingUp = 1 <= restOfIdle
           
            def aLoadedClasses = clMxBean.totalLoadedClassCount 
            def aUnloadedClasses = clMxBean.unloadedClassCount
            if (bLoadedClasses != aLoadedClasses || 
                bUnloadedClasses != aUnloadedClasses) {
                if (traceEnabled) {
                    println "[BM] ${label}: **** class loading/unloding occurred. ****"
                }
                if (AUTO == idle) {
                    countable = false
                }
            }
                
            if (countable) {
                if (!warmingUp) {
                    reals << real
                    if (cpuTimeEnabled) {
                        users << user    
                        cpus << cpu
                        systems << system
                    }
                    rest--
                } else {
                    if (1 == restOfIdle) {
                        if (traceEnabled) {
                            println("[BM] ${label}: **** warm-up stopped. ****")
                        }
                    }
                    restOfIdle--
                } 
            } else {
                if (warmingUp) {
                    if (traceEnabled) {
                        println "[BM] ${label}: **** warm-up continued. ****"
                    }
                } else {
                    restOfIdle = 1
                    if (traceEnabled) {
                        println "[BM] ${label}: **** the measurement is not valid. it is destroyed. ****"
                        println "[BM] ${label}: **** warm-up restarted. ****"
                    }
                }
            }
        }
        def calc = { list ->
            if (trim) {
                list -= list.max()
                list -= list.min()
            }
            def total = list.sum() ?: 0
            if (average) {
                return total / list.size()
            }
            return total
        }
        return [
            label: label,
            time: new BenchmarkTime(
                          real: calc(reals),
                          cpu: calc(cpus),
                          system: calc(systems),
                          user: calc(users),
                  )
        ]
    }
    
    static class Benchmarks extends ArrayList {
        
        /**
         * Sorts by real time.
         * 
         * @return this
         */
        def sort() {
            return sort {it.time.real}    
        }    
        
        /**
         * Pretty-prints.
         * 
         * @param writer a print writer.
         */
        def prettyPrint(PrintWriter writer = new PrintWriter(System.out)) {
            def wids = inject([
                label: 1,
                user: 'user'.size(),
                system: 'system'.size(),
                cpu: 'cpu'.size(),
                real: 'real'.size()
            ]) { wids, bm ->
                wids.label = Math.max(wids.label, bm.label.size())
                wids.user = 
                    Math.max(wids.user, bm.time.user.toString().size())
                wids.system = 
                    Math.max(wids.system, bm.time.system.toString().size())
                wids.cpu = Math.max(wids.cpu, bm.time.cpu.toString().size())
                wids.real = 
                    Math.max(wids.real, bm.time.real.toString().size())
                wids
            }
            writer.printf(
                "%${wids.label}s\t%${wids.user}s\t%${wids.system}s" + 
                "\t%${wids.cpu}s\t%${wids.real}s",
                ' ', 'user', 'system', 'cpu', 'real'
            )
            writer.println()
            each { bm ->
                writer.println()
                writer.printf(
                    "%-${wids.label}s\t%${wids.user}d\t%${wids.system}d" + 
                    "\t%${wids.cpu}d\t%${wids.real}d",
                    bm.label, bm.time.user, bm.time.system, bm.time.cpu, 
                    bm.time.real
                )
            }
            writer.println()
            writer.flush()
        }
    }
}