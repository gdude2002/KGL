import cnames.structs.GLFWwindow
import glew.*
import glew.GLenum
import glew.GLsizei
import glew.GLubyteVar
import glew.GLuint
import glew.GLuintVar
import glfw3.*
import kotlinx.cinterop.*
import platform.opengl32.glDrawArrays
import platform.opengl32.glEnable

const val VERTEX_SHADER = """
#version 420 core
layout (location = 0) in vec3 aPos;

void main()
{
    gl_Position = vec4(aPos.x, aPos.y, aPos.z, 1.0);
}
"""

const val FRAGMENT_SHADER = """
#version 420 core
out vec4 FragColor;

void main()
{
    FragColor = vec4(1.0f, 0.5f, 0.2f, 1.0f);
} 
"""

@Suppress("UNUSED_PARAMETER")
fun handleOpenGlError(
	source: GLenum,
	type: GLenum,
	id: GLuint,
	severity: GLenum,
	length: GLsizei,
	message: CPointer<ByteVarOf<Byte>>?,
	userParam: COpaquePointer?
) {
	println(
		buildString {
			if (type == GL_DEBUG_TYPE_ERROR.toUInt()) {
				append("ERROR | ")
			} else {
				append("INFO  | ")
			}

			append(message?.toKString())
		}
	)
}

fun handleGlfwError(code: Int, description: CPointer<ByteVarOf<Byte>>?) {
	println("ERROR | GLFW error ($code) -> ${description?.toKString()}")
}

fun checkShaderError(shader: GLuint, type: Int) = memScoped {
	val success: IntVar = alloc()
	val message: GLcharVar = alloc()

	glGetShaderiv?.let { it(shader, type.toUInt(), success.ptr) }

	if (success.value != glew.GL_TRUE) {
		glGetShaderInfoLog?.let { it(shader, 512, null, message.ptr) }

		println("ERROR | Shader processing failed: ${message.ptr.toKString()}")
	}
}

fun checkProgramError(program: GLuint, type: Int) = memScoped {
	val success: IntVar = alloc()
	val message: GLcharVar = alloc()

	glGetProgramiv?.let { it(program, type.toUInt(), success.ptr) }

	if (success.value != glew.GL_TRUE) {
		glGetProgramInfoLog?.let { it(program, 512, null, message.ptr) }

		println("ERROR | Program processing failed: ${message.ptr.toKString()}")
	}
}

fun processInput(window: CPointer<GLFWwindow>) {
	if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
		glfwSetWindowShouldClose(window, glfw3.GL_TRUE)
	}
}

fun main() = memScoped {
	// region: GLFW setup

	glfwSetErrorCallback(staticCFunction(::handleGlfwError))

	val glfwResult = glfwInit()

	if (glfwResult != GLFW_TRUE) {
		val errorBuffer = ByteArray(255)

		val error = errorBuffer.usePinned {
			glfwGetError(cValuesOf(it.addressOf(0)))
		}

		println("ERROR | GLFW init error ($error): ${errorBuffer.toKString()}")

		return
	}

	glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
	glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
	glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

	if (Platform.osFamily == OsFamily.MACOSX) {
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, glfw3.GL_TRUE)
	}

	val window = glfwCreateWindow(640, 480, "Hello World", null, null)

	if (window == null) {
		glfwTerminate()

		val errorBuffer = ByteArray(255)

		val error = errorBuffer.usePinned {
			glfwGetError(cValuesOf(it.addressOf(0)))
		}

		println("ERROR | Failed to create window ($error) - ${errorBuffer.toKString()}")

		return
	}

	glfwMakeContextCurrent(window)
	glfwSetFramebufferSizeCallback(window, staticCFunction { _, width, height ->
		glfw3.glViewport(0, 0, width, height)
	})

	// endregion

	// region: GLEW setup

	glewExperimental = glew.GL_TRUE.toUByte()

	val glewResult = glewInit()

	if (glewResult.toInt() != GLEW_OK) {
		println("ERROR | Failed to init GLEW: ${glewGetErrorString(glewResult)?.string}")

		return
	} else {
		println("INFO  | Using GLEW: ${glewGetString(GLEW_VERSION)?.string}")
	}

	glEnable(GL_DEBUG_OUTPUT)

	val debugPtr: IntVar = alloc()
	debugPtr.value = 0

	glDebugMessageCallback?.let { it(staticCFunction(::handleOpenGlError), debugPtr.ptr) }

	glew.glViewport(0, 0, 640, 480)

	// endregion

	// region: Vertex shader

	val vertexShader: GLuint = glCreateShader!!(GL_VERTEX_SHADER.toUInt())

	println("DEBUG | Created vertex shader")

	val vertexSource = allocPointerTo<CPointerVar<ByteVar>>()

	vertexSource.pointed = alloc()
	vertexSource.pointed!!.value = VERTEX_SHADER.cstr.ptr

	glShaderSource!!(vertexShader, 1, vertexSource.value, null)

	println("DEBUG | Bound vertex shader source")

	glCompileShader!!(vertexShader)

	checkShaderError(vertexShader, GL_COMPILE_STATUS)

	println("DEBUG | Compiled vertex shader")

	// endregion

	// region: Fragment shader

	val fragmentShader: GLuint = glCreateShader!!(GL_FRAGMENT_SHADER.toUInt())

	println("DEBUG | Created fragment shader")

	val fragmentSource = allocPointerTo<CPointerVar<ByteVar>>()

	fragmentSource.pointed = alloc()
	fragmentSource.pointed!!.value = FRAGMENT_SHADER.cstr.ptr

	glShaderSource!!(fragmentShader, 1, fragmentSource.value, null)

	println("DEBUG | Bound fragment shader source")

	glCompileShader!!(fragmentShader)

	checkShaderError(fragmentShader, GL_COMPILE_STATUS)

	println("DEBUG | Compiled fragment shader")

	// endregion

	// region: Finalize shaders

	val shaderProgram: GLuint = glCreateProgram!!()

	glAttachShader!!(shaderProgram, vertexShader)
	glAttachShader!!(shaderProgram, fragmentShader)

	glLinkProgram!!(shaderProgram)

	println("DEBUG | Linked shader program")

	checkProgramError(shaderProgram, GL_LINK_STATUS)

	glDeleteShader!!(vertexShader)
	glDeleteShader!!(fragmentShader)

	println("DEBUG | Shader objects deleted")

	// endregion

	// region: Vertices

	val vertices = this.allocArray<FloatVar>(9)

	val vertexArray = arrayOf(
		-0.5f, -0.5f, 0.0f,
		0.5f, -0.5f, 0.0f,
		0.0f, 0.5f, 0.0f
	)

	vertexArray.forEachIndexed(vertices::set)

	val vao: GLuintVar = alloc()
	val vbo: GLuintVar = alloc()

	glGenVertexArrays!!(1, vao.ptr)
	glGenBuffers!!(1, vbo.ptr)

	glBindVertexArray!!(vao.value)

	glBindBuffer!!(GL_ARRAY_BUFFER.toUInt(), vbo.value)

	glBufferData!!(
		GL_ARRAY_BUFFER.toUInt(),
		(vertexArray.size * Float.SIZE_BYTES).toLong(),
		vertices,
		GL_STATIC_DRAW.toUInt()
	)

	println("DEBUG | Created & bound VAO & VBO")

	glVertexAttribPointer!!(
		0u,
		3,
		glew.GL_FLOAT.toUInt(),
		glew.GL_FALSE.toUByte(),
		0,
		null
	)

	glEnableVertexAttribArray!!(0u)

	glBindBuffer!!(GL_ARRAY_BUFFER.toUInt(), 0u)
	glBindVertexArray!!(0u)

	// endregion

	// region: Rendering

	// Wireframe
	// glew.glPolygonMode(glew.GL_FRONT_AND_BACK, glew.GL_LINE)

	while (glfwWindowShouldClose(window) != GLFW_TRUE) {
		processInput(window)

		glew.glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
		glew.glClear(glew.GL_COLOR_BUFFER_BIT);

		glUseProgram!!(shaderProgram)
		glBindVertexArray!!(vao.value)

		glDrawArrays(glew.GL_TRIANGLES, 0, 3)

		glfwSwapBuffers(window)
		glfwPollEvents()
	}

	// endregion

	glDeleteVertexArrays!!(1, vao.ptr)
	glDeleteBuffers!!(1, vbo.ptr)
	glDeleteProgram!!(shaderProgram)

	glfwTerminate()
}

val CPointer<GLubyteVar>.string: String
	get() = reinterpret<ByteVar>().toKString()
