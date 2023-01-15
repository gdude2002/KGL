plugins {
	kotlin("multiplatform") version "1.8.0"
}

group = "me.gserv"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

kotlin {
	val hostOs = System.getProperty("os.name")
	val isMingwX64 = hostOs.startsWith("Windows")

	val nativeTarget = when {
		hostOs == "Mac OS X" -> macosX64("native")
		hostOs == "Linux" -> linuxX64("native")
		isMingwX64 -> mingwX64("native")

		else -> throw GradleException("Host OS is not supported by Kotlin/Native.")
	}

	nativeTarget.apply {
		compilations.getByName("main") {
			cinterops {
				val glfw3 by creating
				val glew by creating
				val opengl by creating
			}
		}

		binaries {
			executable {
				entryPoint = "main"
			}
		}
	}

	sourceSets {
		val nativeMain by getting
		val nativeTest by getting
	}
}
