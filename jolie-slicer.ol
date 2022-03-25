
from types.JavaException import JavaExceptionType, WeakJavaExceptionType
from types.IOException import IOExceptionType
from file import FileNotFoundType

type SliceRequest: void {
	program: string
	config?: string
	disembedConfig?: string
	visualize?: string
	outputDirectory? : string
}

interface SlicerInterface {
RequestResponse:
	slice( SliceRequest )( void ) throws
		NoSuchFileException( JavaExceptionType )
		IOException( IOExceptionType )
		ParserException( JavaExceptionType )
		InvalidConfigurationFileException( JavaExceptionType )
}

service Slicer {
	inputPort ip {
		location: "local"
		interfaces: SlicerInterface
	}

	foreign java {
		class: "joliex.slicer.JolieSlicer"
	}
}