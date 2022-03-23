/*
 * Copyright (C) 2021 Valentino Picotti
 * Copyright (C) 2021 Fabrizio Montesi <famontesi@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package joliex.slicer;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import jolie.lang.parse.ast.EmbedServiceNode;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.ServiceNode;

import org.json.simple.JSONArray;
import java.io.File;
import java.io.FileInputStream;

/**
 * Slicer
 */
public class Slicer {
	final Program program;
	final Path configPath;
	final Path outputDirectory;
	final JSONObject config;
	final DependenciesResolver dependenciesResolver;
	Map< String, Program > slices = null;

	static final String DOCKERFILE_FILENAME = "Dockerfile";
	static final String DOCKERCOMPOSE_FILENAME = "docker-compose.yml";

	private Slicer( Program p, Path configPath, Path outputDirectory )
		throws FileNotFoundException, InvalidConfigurationFileException {
		this.program = p;
		this.configPath = configPath;
		this.outputDirectory = outputDirectory;
		Object o = JSONValue.parse( new FileReader( configPath.toFile() ) );
		if( !(o instanceof JSONObject) ) {
			String msg = "Top level definition must be a json object";
			throw new InvalidConfigurationFileException( msg );
		}
		this.config = (JSONObject) o;
		p.children()
			.stream()
			.filter( ServiceNode.class::isInstance )
			.map( ServiceNode.class::cast )
			.forEach( Slicer::removeAutogeneratedInputPorts );
		this.dependenciesResolver = new DependenciesResolver( p );
	}

	public static Slicer create( Program p, Path configPath, Path outputDirectory )
		throws FileNotFoundException, InvalidConfigurationFileException {
		Slicer slicer = new Slicer( p, configPath, outputDirectory );
		slicer.sliceProgram();
		return slicer;
	}

	public void validateConfigurationFile( Map< String, ServiceNode > declaredServices )
		throws InvalidConfigurationFileException {
		final StringBuilder msg = new StringBuilder();
		final Path programPath = Paths.get( program.context().sourceName() ).getFileName();

		final Set< String > undeclaredServices = ((Set<?>) config.keySet())
				.stream()
				.filter( String.class::isInstance )
				.map( String.class::cast )
				.collect( Collectors.toSet() );

		// undeclaredServices.removeAll( declaredServices.entrySet() );
		if( !undeclaredServices.isEmpty() ) {
			for( String service : undeclaredServices ) {
				msg.append( "Service " )
					.append( service )
					.append( " in " )
					.append( configPath.getFileName() )
					.append( " is not declared in program " )
					.append( programPath )
					.append( System.lineSeparator() );
			}
		}

		ArrayList< String > servicesWithoutParameter = declaredServices.entrySet().stream()
			.filter( e -> config.containsKey( e.getKey() ) && e.getValue().parameterConfiguration().isPresent() )
			.map( e -> e.getValue().name() )
			.collect( Collectors.toCollection( ArrayList::new ) );
		if( !servicesWithoutParameter.isEmpty() ) {

			for( String service : servicesWithoutParameter ) {
				msg.append( "Service " + service + " in " + programPath ).append( " does not declare a parameter" )
					.append( System.lineSeparator() );
			}
		}
		if( !msg.toString().isEmpty() ) {
			throw new InvalidConfigurationFileException( msg.toString() );
		}
	}

	private void sliceProgram() {
		/* Slices only the services mentioned in the config */
		slices = new HashMap<>();
		program.children()
			.stream()
			.filter( ServiceNode.class::isInstance )
			.map( ServiceNode.class::cast )
			// Slice only services that are present in the configuration
			.filter( s -> config.containsKey( s.name() ) )
			.forEach( s -> {
				// Sort dependencies by their line to preserve the ordering given by the programmer
				List< OLSyntaxNode > newProgram =
					dependenciesResolver.getServiceDependencies( s )
						.stream()
						.sorted( Comparator.comparing( dep -> dep.context().line() ) )
						.collect( Collectors.toList() );
				newProgram.add( s );
				slices.put( s.name(), new Program( program.context(), newProgram ) );
			} );
	}

	private static void removeAutogeneratedInputPorts( ServiceNode service ) {
		ArrayList< OLSyntaxNode > toBeRemoved = service.program()
			.children()
			.stream()
			.filter( EmbedServiceNode.class::isInstance )
			.map( EmbedServiceNode.class::cast )
			.filter( EmbedServiceNode::isNewPort )
			.map( EmbedServiceNode::bindingPort )
			.collect( ArrayList::new, ArrayList::add, ArrayList::addAll );
		service.program().children().removeAll( toBeRemoved );
	}

	public void generateServiceDirectories()
		throws IOException {
		Files.createDirectories( outputDirectory );
		for( Map.Entry< String, Program > service : slices.entrySet() ) {
			// Iterate over the program, find the root service of the program w.r.t to the hashmap key
			// find the input port not equal to local and find the exposing port.
			final List<String> ports = new ArrayList<>();
			service.getValue().children().stream()
				.filter( ServiceNode.class::isInstance )
				.map( ServiceNode.class::cast )
				.forEach( serviceNode -> {
					if(serviceNode.name().equals(service.getKey())){
						serviceNode.program().children().stream().filter( InputPortInfo.class::isInstance )
						.map( InputPortInfo.class::cast ).forEach((ip) -> {
							if(!ip.location().toString().equals("local")){
								String[] parts = ip.location().toString().split(":");
								String port = parts[parts.length - 1].replaceAll("[^0-9]", "");
								ports.add(port);
							}
						});
					}
				});
			String port = ports.get(0);

			JoliePrettyPrinter pp = new JoliePrettyPrinter();
			// Create Service Directory
			Path serviceDir = outputDirectory.resolve( service.getKey().toLowerCase() );
			Files.createDirectories( serviceDir );
			// Copy configuration file
			Path newConfigPath = serviceDir.resolve( configPath.getFileName() );
			Files.copy( configPath, newConfigPath, StandardCopyOption.REPLACE_EXISTING );
			// Output Jolie
			Path jolieFilePath = serviceDir.resolve( service.getKey() + ".ol" );
			try( OutputStream os =
				Files.newOutputStream( jolieFilePath, CREATE, TRUNCATE_EXISTING, WRITE ) ) {
				pp.visit( service.getValue() );
				os.write( pp.toString().getBytes() );
			}

			// Generate dependencies for service
			// TODO - Needs to only do this is it has a dependency field.
			boolean didItGenerateDependencies = generateDependencyFolder(service.getKey(), serviceDir.toString());

			// Output Dockerfile
			if (didItGenerateDependencies) {
			try( OutputStream os =
				Files.newOutputStream( serviceDir.resolve( DOCKERFILE_FILENAME ),
					CREATE, TRUNCATE_EXISTING, WRITE ) ) {
				String dfString = String.format(
					"FROM jolielang/jolie%n"
						+ "COPY %1$s .%n"
						+ "COPY %2$s .%n"
						+ "COPY %3$s .%n"
						+ "EXPOSE %4$s %n"
						+ "CMD [\"jolie\", \"--params\", \"%2$s\",\"--service\", \"%3$s\", \"%1$s\"]",
					jolieFilePath.getFileName(),
					configPath.getFileName(), 
					"/lib/",
					port,
					service.getKey());
				os.write( dfString.getBytes() );
			}
			} else {
				try( OutputStream os =
					Files.newOutputStream( serviceDir.resolve( DOCKERFILE_FILENAME ),
						CREATE, TRUNCATE_EXISTING, WRITE ) ) {
					String dfString = String.format(
						"FROM jolielang/jolie%n"
							+ "COPY %1$s .%n"
							+ "COPY %2$s .%n"
							+ "EXPOSE %3$s %n"
							+ "CMD [\"jolie\", \"--params\", \"%2$s\", \"--service\", \"%4$s\",  \"%1$s\"]",
						jolieFilePath.getFileName(),
						configPath.getFileName(),
						port, 
						service.getKey() );
					os.write( dfString.getBytes() );
					}
				}
			}
		// Output docker-compose
		try( Formatter fmt =
			new Formatter( outputDirectory.resolve( DOCKERCOMPOSE_FILENAME ).toFile() ) ) {
			createDockerCompose( fmt );
		}
	}

	private void createDockerCompose( Formatter fmt ) {
		String padding = "";
		Boolean volumes = false;
		ArrayList<String> volumeNameList = new ArrayList<String>();
		fmt.format( "version: \"3.9\"%n" )
			.format( "services:%n" );
		for( Map.Entry< String, Program > service : slices.entrySet() ) {
			fmt.format( "%2s%s:%n", padding, service.getKey().toLowerCase() )
				.format( "%4s", padding )
				.format( "build: ./%s%n", service.getKey().toLowerCase() );
			// If config for current service have defined a database component
			// generate database component, and add volumes.
			JSONObject tempService = (JSONObject) config.get(service.getKey());
			if (tempService.containsKey("database")) {
				JSONObject db = (JSONObject) tempService.get("database");
				// Look at RBDSM and generate corresponding docker-compose component
				// At the moment only supports mysql.
				if (((String) db.get("RDBMS_IMAGE")).toLowerCase().contains("mysql")) {
					volumes = true;
					volumeNameList.add(service.getKey().toLowerCase());
					generateMYSQLComponent(fmt, db, service.getKey().toLowerCase());
				}
			}
		}
		// If any volumes are needed, add them to docker-compose in the end of the file.
		if (volumes) {
			fmt.format("volumes:%n");
			// If any database is created, create a volume for each.
			for (String vol : volumeNameList) {
				String temp = vol + "-db-vol:"; // This is the format of volumes in this program, [ServiceName]-db-vol
				fmt.format("  %s%n",temp);
			}
		}
	}

	/**
	 * Function that will generate a MYSQL component for a docker-compose file.
	 *
	 * @param (Formatter fmt) Formatter for docker-compose file
	 * @param (JSONObject db) The extracted part concerning database information in config file for current service
	 * @param (String serviceName) Name of the service that needs a MYSQL component
	 * @return (void)
	*/
	private void generateMYSQLComponent(Formatter fmt, JSONObject db, String serviceName) {
		fmt.format("  %s-db:%n", serviceName)
                .format("    image: %s%n", db.get("RDBMS_IMAGE"))
                .format("    environment:%n")
                    .format("      MYSQL_DATABASE: \'%s\'%n", db.get("DATABASE"))
                    .format("      MYSQL_USER: \'%s\'%n", db.get("USER"))
                    .format("      MYSQL_PASSWORD: \'%s\'%n", db.get("PASSWORD"))
                    .format("      MYSQL_ROOT_PASSWORD: \'%s\'%n", db.get("ROOT_PASSWORD"))
                    .format("      ports: \'%s\'%n", "3306:3306")
                .format("    expose:%n")
                    .format("      - \'3306\'%n")
                .format("    volumes:%n")
                    .format("      - %s-db-vol:/var/lib/mysql%n", serviceName);
	}

	/**
	 * This function is used if there inside the config file are specified a service needs
	 * external resources. These will need to be placed in the current working directory inside a 
	 * folder called /dependencies/. 
	 * It will then create a /lib/ folder within the generated service folder. When this is copied
	 * inside the docker container, jolie will recognize the lib folder and use the external resources 
	 * within the container. 
	 *
	 * @param (String serviceName) Name of the current service, used to extract information from config.json
	 * @param (String dependencyDest) Path to the dependency destination, e.g., monolith/serviceName.  
	 * @return (Boolean) True if any dependencies were resolved, false otherwise.
	*/
	private boolean generateDependencyFolder(String serviceName, String dependencyDest) throws FileNotFoundException, IOException {
        try {
			String filePath = "config.json";
			JSONObject o = (JSONObject) JSONValue.parse( new FileReader( filePath ) );
			JSONObject service = (JSONObject) o.get(serviceName);
			// TODO - This check throws an error if filename != monolith, and json != config.json
			if (service.containsKey("dependencies")) {
				JSONArray dependencies = (JSONArray) service.get("dependencies");
				
				// Get all file names inside /dependency folder
				Path currentRelativePath = Paths.get("");
				String path = currentRelativePath.toAbsolutePath().toString();
				String dependenciesPath = path + "/dependencies";
				File file = new File(dependenciesPath);
				ArrayList<String> dependencyList = new ArrayList<String>(Arrays.asList(file.list()));
	
				// Create a directory containing all dependencies for current service
				// called lib - This is where dependencies are placed for all jolie modules.
				File depDir = new File((path + "/" + dependencyDest + "/lib"));
				if (!depDir.exists()){
					depDir.mkdirs();
				}
				String depDirPath = depDir.toString();
	
				// Copy required dependencies from this service into the service's 
				// dependency folder created above
				//String dependencyDir = path + "/" + dependencyDest;
				for (int i=0; i < dependencies.size(); i++) {
					if (dependencyList.contains(dependencies.get(i))){
						String dependencyPath = dependenciesPath + "/" + dependencies.get(i);
						File depSource = new File(dependencyPath);
						File depDest = new File(depDirPath + "/" + dependencies.get(i));
						copy(depSource, depDest);
					}
				}
				return true;
			}
		} catch (FileNotFoundException e ) {
			System.out.println("Exception thrown trying to parse JSON : " + e);
		} catch (IOException e ) {
			System.out.println("Failed trying to copy from src to dest folder : " + e);
		}
	return false;
	}
	/* 
    Function used to copy file from one destination to another
    https://www.java67.com/2016/09/how-to-copy-file-from-one-location-to-another-in-java.html
    */
    private static void copy(File src, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dest);

            // buffer size 1K
            byte[] buf = new byte[1024];

            int bytesRead;
            while ((bytesRead = is.read(buf)) > 0) {
                os.write(buf, 0, bytesRead);
            }
        } finally {
            is.close();
            os.close();
        }
    }
	public Map< String, Program > getSlices() {
		return slices;
	}
}
