/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class OverlappingOutputsIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    def setup() {
        buildFile << """
            @CacheableTask
            class OutputDirectoryTask extends DefaultTask {
                @OutputDirectory
                File outputDir = project.buildDir
                
                @Input
                String message = "Generated by " + path
                
                @Input
                String outputFileName = name + ".txt"
                
                @TaskAction
                void generate() {
                    new File(outputDir, outputFileName).text = message
                }
            }
            
            @CacheableTask
            class OutputFileTask extends DefaultTask {
                @Input
                String message = "Generated by " + path
                
                @Input
                String outputFileName = name + ".txt"
                
                @OutputFile
                File getOutputFile() {
                    new File(project.buildDir, outputFileName)
                } 
                
                @TaskAction
                void generate() {
                    outputFile.text = message
                }
            }
        """
    }

    private Object[] useOverlappingOutputDirectories() {
        buildFile << """
            task first(type: OutputDirectoryTask)
            task second(type: OutputDirectoryTask)
            task cleanSecond(type: Delete) {
                delete second
            }
        """
        return [":first", file("build/first.txt"),
                ":second", file("build/second.txt")]
    }

    def "overlapping output directory with first, second then first, second"() {
        def (String first, TestFile firstOutput,
             String second, TestFile secondOutput) = useOverlappingOutputDirectories()

        when:
        withBuildCache().succeeds(first, second)
        then:
        firstOutput.assertExists()
        secondOutput.assertExists()
        // Only the first task can be cached since the second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(first, second)
        then:
        // Output should match the first execution
        firstOutput.assertExists()
        secondOutput.assertExists()
        // first can be loaded from the cache
        result.assertTaskSkipped(first)
        // second cannot be loaded from the cache due to a cache miss
        result.assertTaskNotSkipped(second)
    }

    def "overlapping output directory with first, second then second, first"() {
        def (String first, TestFile firstOutput,
             String second, TestFile secondOutput) = useOverlappingOutputDirectories()

        when:
        withBuildCache().succeeds(first, second)
        then:
        firstOutput.assertExists()
        secondOutput.assertExists()
        // Only the first task can be cached since the second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(second, first)
        then:
        // Output should match the first execution
        firstOutput.assertExists()
        secondOutput.assertExists()
        // Neither task can be loaded from the cache because the
        // second task has a cache miss and the first task detects the overlap
        result.assertTasksNotSkipped(second, first)
    }

    def "overlapping output directory with first, second then second only"() {
        def (String first, TestFile firstOutput,
             String second, TestFile secondOutput) = useOverlappingOutputDirectories()

        when:
        withBuildCache().succeeds(first, second)
        then:
        firstOutput.assertExists()
        secondOutput.assertExists()
        // Only the first task can be cached since the second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(second)
        then:
        firstOutput.assertDoesNotExist()
        secondOutput.assertExists()
        // second cannot be loaded from the cache due to a cache miss
        result.assertTasksNotSkipped(second)
        // second should now be cached because there was no overlap since
        // first did not execute
        listCacheFiles().size() == 2
    }

    def "overlapping output directory with first cleanSecond second then first second"() {
        def (String first, TestFile firstOutput,
             String second, TestFile secondOutput) = useOverlappingOutputDirectories()
        def cleanSecond = ":cleanSecond"

        when:
        withBuildCache().succeeds(first, cleanSecond, second)
        then:
        // Both tasks can be cached because clean removes the output from first
        // before second executes.
        listCacheFiles().size() == 2

        when:
        cleanBuildDir()
        withBuildCache().succeeds(first, second)
        then:
        firstOutput.assertExists()
        secondOutput.assertExists()
        // first is loaded from the cache
        result.assertTaskSkipped(first)
        // second is not loaded from the cache due to the overlap with first
        result.assertTaskNotSkipped(second)
    }

    private Object[] useOverlappingOutputFileAndDirectory() {
        buildFile << """
            task fileTask(type: OutputFileTask)
            task dirTask(type: OutputDirectoryTask)
            task cleanDirTask(type: Delete) {
                delete dirTask
            }
        """
        return [ ":fileTask", file("build/fileTask.txt"),
                 ":dirTask", file("build/dirTask.txt") ]
    }

    def "overlapping output with fileTask, dirTask then fileTask, dirTask"() {
        def (String fileTask, TestFile fileTaskOutput,
             String dirTask, TestFile dirTaskOutput) = useOverlappingOutputFileAndDirectory()

        when:
        withBuildCache().succeeds(fileTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Only one task can be cached
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(fileTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // fileTask can be from the cache, but the dirTask cannot due to a cache miss
        result.assertTaskSkipped(fileTask)
        result.assertTaskNotSkipped(dirTask)
    }

    def "overlapping output with fileTask, dirTask then dirTask, fileTask"() {
        def (String fileTask, TestFile fileTaskOutput,
             String dirTask, TestFile dirTaskOutput) = useOverlappingOutputFileAndDirectory()

        when:
        withBuildCache().succeeds(fileTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Only one task can be cached
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(dirTask, fileTask)
        then:
        // Outcome should look the same as if the build was run in the opposite order (fileTask then dirTask)
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // fileTask can be from the cache, but the dirTask cannot due to a cache miss
        result.assertTaskSkipped(fileTask)
        result.assertTaskNotSkipped(dirTask)
        // Now the dirTask can be cached (since it executed first)
        listCacheFiles().size() == 2

        when:
        cleanBuildDir()
        withBuildCache().succeeds(dirTask, fileTask)
        then:
        // Outcome should look the same again
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Both can be from the cache because the dirTask ran first and the fileTask doesn't directly overlap with a file produced by dirTask
        result.assertTasksSkipped(dirTask, fileTask)
    }

    def "overlapping output with fileTask, dirTask then dirTask only"() {
        def (String fileTask, TestFile fileTaskOutput,
             String dirTask, TestFile dirTaskOutput) = useOverlappingOutputFileAndDirectory()

        when:
        withBuildCache().succeeds(fileTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Only one task can be cached
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(dirTask)
        then:
        // fileTask's outputs shouldn't exist since it didn't execute
        fileTaskOutput.assertDoesNotExist()
        dirTaskOutput.assertExists()
        // dirTask will not be from the cache due to a cache miss
        result.assertTasksNotSkipped(dirTask)
        // dirTask is cached now (since fileTask didn't overlap this time)
        listCacheFiles().size() == 2
    }

    def "overlapping output with dirTask, fileTask then fileTask then dirTask"() {
        def (String fileTask, TestFile fileTaskOutput,
             String dirTask, TestFile dirTaskOutput) = useOverlappingOutputFileAndDirectory()

        when:
        withBuildCache().succeeds(dirTask, fileTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Both tasks can be cached because the dirTask doesn't use the same output file as fileTask
        listCacheFiles().size() == 2

        when:
        cleanBuildDir()
        withBuildCache().succeeds(fileTask)
        then:
        fileTaskOutput.assertExists()
        // dirTask didn't execute
        dirTaskOutput.assertDoesNotExist()
        // fileTask can be loaded from the cache
        result.assertTaskSkipped(fileTask)

        when:
        withBuildCache().succeeds(dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // dirTask cannot be loaded from the cache due to an overlap with fileTask
        result.assertTaskNotSkipped(dirTask)
    }

    // This fails because cleanDirTask will remove fileTask's outputs.
    // So, unless we change this to only clean the *real* outputs of dirTask, this won't work.
    @NotYetImplemented
    def "overlapping output with fileTask, dirTask then fileTask, cleanDirTask, dirTask"() {
        def cleanDirTask = ":cleanDirTask"
        def (fileTask, fileTaskOutput,
             dirTask, dirTaskOutput) = useOverlappingOutputFileAndDirectory()
        when:
        withBuildCache().succeeds(fileTask, cleanDirTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // Both tasks can be cached since fileTask's outputs are removed before dirTask executes
        listCacheFiles().size() == 2

        when:
        cleanBuildDir()
        withBuildCache().succeeds(fileTask, dirTask)
        then:
        fileTaskOutput.assertExists()
        dirTaskOutput.assertExists()
        // fileTask can be loaded from the cache
        result.assertTaskSkipped(fileTask)
        // dirTask cannot be loaded from the cache because fileTask's outputs overlap
        result.assertTaskNotSkipped(dirTask)
    }

    private Object[] useOverlappingOutputFiles() {
        buildFile << """
            task first(type: OutputFileTask)
            task second(type: OutputFileTask) {
                // second's message needs to be different so we don't detect the file has unchanged
                message = "Generated by task " + path
            }
            task cleanSecond(type: Delete) {
                delete second
            }
            tasks.withType(OutputFileTask) {
                outputFileName = "AB.txt"
            }
        """
        return [":first", ":second", file("build/AB.txt")]
    }

    def "overlapping output files with first, second then first, second"() {
        def (String first, String second, TestFile sharedOutput) = useOverlappingOutputFiles()

        when:
        withBuildCache().succeeds(first, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // Only first can be cached because second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(first, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // first can be loaded from the cache
        result.assertTaskSkipped(first)
        // second cannot be loaded from the cache because it overlaps with first
        result.assertTaskNotSkipped(second)

        when:
        withBuildCache().succeeds(first)
        then:
        // first overwrites second's output if executed on its own
        sharedOutput.text == "Generated by ${first}"
        // first is not loaded from cache because of overlap
        result.assertTaskNotSkipped(first)
    }

    def "overlapping output files with first, second then second, first"() {
        def (String first, String second, TestFile sharedOutput) = useOverlappingOutputFiles()

        when:
        withBuildCache().succeeds(first, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // Only first can be cached because second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(second, first)
        then:
        sharedOutput.text == "Generated by ${first}"
        // second will not be loaded from the cache due to a cache miss
        result.assertTaskNotSkipped(second)
        // first is not loaded from the cache because second overlaps
        result.assertTaskNotSkipped(first)
    }

    def "overlapping output files with first, second then second only"() {
        def (String first, String second, TestFile sharedOutput) = useOverlappingOutputFiles()

        when:
        withBuildCache().succeeds(first, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // Only first can be cached because second detects the overlap
        listCacheFiles().size() == 1

        when:
        cleanBuildDir()
        withBuildCache().succeeds(second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // second cannot be loaded from the cache due to cache miss
        result.assertTasksNotSkipped(second)
        // second can be cached now because first did not execute
        listCacheFiles().size() == 2
    }

    def "overlapping output files with first, cleanSecond, second then first, second"() {
        def (String first, String second, TestFile sharedOutput) = useOverlappingOutputFiles()
        def cleanSecond = ":cleanSecond"

        when:
        withBuildCache().succeeds(first, cleanSecond, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // Both tasks can be cached because cleanSecond removes the outputs of first
        // before executing second
        listCacheFiles().size() == 2

        when:
        cleanBuildDir()
        withBuildCache().succeeds(first, second)
        then:
        sharedOutput.text == "Generated by task ${second}"
        // first can be loaded from the cache and second can't be loaded because of overlap
        result.assertTasksSkipped(first)
        result.assertTaskNotSkipped(second)
    }

    private void cleanBuildDir() {
        file("build").deleteDir()
    }

    // We ignore external.txt as an input because the file doesn't change after executing A
    // So when external.txt changes, we don't count that as a change in outputs.
    // @NotYetImplemented
    def "overlapping directory with external process and a pre-existing file"() {
        buildFile << """
            task someTask(type: OutputDirectoryTask)
        """
        def someTask = ":someTask"
        def externalFile = file("build/external.txt")
        def someTaskOutput = file("build/someTask.txt")
        externalFile.text = "Created by something else"
        when:
        withBuildCache().succeeds(someTask)
        then:
        // someTask cannot be cached.
        listCacheFiles().size() == 0
        externalFile.assertExists()
        someTaskOutput.assertExists()

        when:
        externalFile.text = "changed"
        someTaskOutput.delete()
        withBuildCache().succeeds(someTask)
        then:
        result.assertTaskNotSkipped(someTask)
        externalFile.text == "changed"
        someTaskOutput.assertExists()

        when:
        cleanBuildDir()
        withBuildCache().succeeds(someTask)
        then:
        result.assertTaskNotSkipped(someTask)
        externalFile.assertDoesNotExist()
        someTaskOutput.assertExists()
        // someTask can be cached now
        listCacheFiles().size() == 1
    }

    def "overlapping file with external process and a pre-existing file"() {
        buildFile << """
            task someTask(type: OutputFileTask)
        """
        def someTask = ":someTask"
        def someTaskOutput = file("build/someTask.txt")
        someTaskOutput.text = "Created by something else"

        when:
        withBuildCache().succeeds(someTask)
        then:
        // someTask cannot be cached because its outputs were created by something else
        listCacheFiles().size() == 0
        someTaskOutput.text == "Generated by ${someTask}"
    }

    @Unroll
    def "overlapping #taskType with external process and a build-generated file"() {
        buildFile << """
            task someTask(type: $taskType)
        """
        def someTask = ":someTask"
        def someTaskOutput = file("build/someTask.txt")

        when:
        withBuildCache().succeeds(someTask)
        then:
        // A can be cached.
        listCacheFiles().size() == 1
        someTaskOutput.assertExists()

        when:
        someTaskOutput.text = "changed"
        withBuildCache().succeeds(someTask)
        then:
        result.assertTaskNotSkipped(someTask)
        someTaskOutput.text == "Generated by ${someTask}"

        when:
        // Looks the same as clean
        someTaskOutput.delete()
        withBuildCache().succeeds(someTask)
        then:
        result.assertTaskSkipped(someTask)
        someTaskOutput.text == "Generated by ${someTask}"

        where:
        taskType << [ "OutputDirectoryTask", "OutputFileTask" ]
    }

    // We don't consider just empty directories as being an "overlapping" output
    // because if the root directory exists before task execution, we would count that
    // as an overlapping output, even though it doesn't effect the output.
    @NotYetImplemented
    def "overlapping directory with external process that creates a directory"() {
        buildFile << """
            task someTask(type: OutputDirectoryTask)
        """
        def someTask = ":someTask"
        def someTaskOutput = file("build/someTask.txt")

        when:
        withBuildCache().succeeds(someTask)
        then:
        // A can be cached.
        listCacheFiles().size() == 1
        someTaskOutput.assertExists()

        when:
        cleanBuildDir()
        file("build/emptyDir").createDir()
        withBuildCache().succeeds(someTask)
        then:
        result.assertTaskNotSkipped(someTask)
        someTaskOutput.assertExists()
    }
}
