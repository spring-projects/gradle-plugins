package org.springframework.build.gradle.springio.platform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test

import org.apache.tools.ant.taskdefs.condition.Os
/**
 * @author Rob Winch
 * @author Andy Wilkinson
 */
class SpringioPlatformPlugin implements Plugin<Project> {
	static String CHECK_TASK_NAME = 'springioCheck'
	static String TEST_TASK_NAME = 'springioTest'
	static String INCOMPLETE_EXCLUDES_TASK_NAME = 'springioIncompleteExcludesCheck'
	static String ALTERNATIVE_DEPENDENCIES_TASK_NAME = 'springioAlternativeDependenciesCheck'
	static String CHECK_DEPENDENCY_VERSION_MAPPING_TASK_NAME = 'springioDependencyVersionMappingCheck'

	@Override
	void apply(Project project) {
		project.plugins.withType(JavaPlugin) {
			applyJavaProject(project)
		}
	}

	def applyJavaProject(Project project) {
		Configuration springioTestRuntimeConfig = project.configurations.create('springioTestRuntime', {
			extendsFrom project.configurations.testRuntime
		})

		springioTestRuntimeConfig.incoming.beforeResolve(
			new MapPlatformDependenciesBeforeResolveAction(project: project, configuration: springioTestRuntimeConfig))

		Task springioTest = project.tasks.create(TEST_TASK_NAME)

		['JDK7','JDK8'].each { jdk ->
			maybeCreateJdkTest(project, springioTestRuntimeConfig, jdk, springioTest)
		}

		Task incompleteExcludesCheck = project.tasks.create(INCOMPLETE_EXCLUDES_TASK_NAME, IncompleteExcludesTask)
		Task alternativeDependenciesCheck = project.tasks.create(ALTERNATIVE_DEPENDENCIES_TASK_NAME, AlternativeDependenciesTask)
		Task dependencyVersionMappingCheck = project.tasks.create(CHECK_DEPENDENCY_VERSION_MAPPING_TASK_NAME, DependencyVersionMappingCheckTask)

		project.tasks.create(CHECK_TASK_NAME) {
			dependsOn dependencyVersionMappingCheck
			dependsOn springioTest
			dependsOn incompleteExcludesCheck
			dependsOn alternativeDependenciesCheck
		}
	}

	private void maybeCreateJdkTest(Project project, Configuration springioTestRuntimeConfig, String jdk, Task springioTest) {
		def whichJdk = "${jdk}_HOME"
		if(!project.hasProperty(whichJdk)) {
			return
		}
		def jdkHome = project."${whichJdk}"
		def javaExecFilePath = '/bin/java'
		if (Os.isFamily(Os.FAMILY_WINDOWS)) {
			javaExecFilePath += '.exe'
		}
		def exec = new File(jdkHome, javaExecFilePath)
		if(!exec.exists()) {
			throw new IllegalStateException("The path $exec does not exist! Please ensure to define a valid JDK home as a commandline argument using -P${whichJdk}=<path>")
		}

		Test springioJdkTest = project.tasks.create("springio${jdk}Test", Test)
		project.configure(springioJdkTest) {
			classpath = project.sourceSets.test.output + project.sourceSets.main.output + springioTestRuntimeConfig
			reports {
				html.destination = project.file("$project.buildDir/reports/springio-$jdk-tests/")
				junitXml.destination = project.file("$project.buildDir/springio-$jdk-test-results/")
			}
		}
		springioTest.dependsOn springioJdkTest
	}
}
