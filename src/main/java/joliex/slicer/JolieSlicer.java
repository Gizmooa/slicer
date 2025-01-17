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

import jolie.Interpreter;
import jolie.cli.CommandLineException;
import jolie.cli.CommandLineParser;
import jolie.lang.CodeCheckingException;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.SemanticVerifier;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.module.ModuleException;
import jolie.lang.parse.util.ParsingUtils;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.embedding.RequestResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;


public class JolieSlicer extends JavaService {

    private static final boolean INCLUDE_DOCUMENTATION = false;
    private static final String[] EMPTY_INCLUDE_PATHS = new String[0];
    private static final ClassLoader CLASS_LOADER = JolieSlicer.class.getClassLoader();

    @RequestResponse
    public void slice( Value request ) throws FaultException {
        final Path programPath = Path.of( request.getFirstChild( "program" ).strValue() );
        final Path visualize = Path.of( request.getFirstChild( "visualize" ).strValue() );
        final Path disembedConfig = Path.of( request.getFirstChild( "disembedConfig" ).strValue() );
        // If there are no config given, we will create one at the path config.json.
        // This is because the program can be called without a config, but with a slices.config instead
        // where the config will be generated.
        final Path configPath;
        if (request.getFirstChild( "config" ).strValue().equals("")){
            configPath = Path.of("config.json");
        }
        else{
            configPath = Path.of( request.getFirstChild( "config" ).strValue() );
        }
        final Path outputDirectory;
        if( request.hasChildren( "outputDirectory" ) ) {
            outputDirectory = Path.of(request.getFirstChild( "outputDirectory" ).strValue() );
        } else { // Generete the sliced program into a directory with the same name of the program
            String filename = programPath.getFileName().toString();
            int fileExtensionIndex = filename.lastIndexOf( ".ol" );
            filename = filename.substring( 0, fileExtensionIndex );
            outputDirectory = programPath.resolveSibling( filename );
        }
        ArrayList<String> newArgs = new ArrayList<>();
        String[] interpreterArgs = Interpreter.getInstance().optionArgs();
        
        for(int i = 0; i < interpreterArgs.length; i++ ) {
            if( "-p".equals(interpreterArgs[i]) ) {
                newArgs.add(interpreterArgs[i]);
                i++;
                newArgs.add(interpreterArgs[i]);
            }
        }
        newArgs.add( programPath.toString() );

        /* try ( InputStream stream = Files.newInputStream( programPath ) ) { */
            final Path absolute = programPath.toAbsolutePath();
            final Path programDirectory = absolute.getParent();

            
            try( CommandLineParser cmdLnParser =
			            new CommandLineParser( newArgs.toArray(new String[0]), JolieSlicer.class.getClassLoader() ) ) {

			Interpreter.Configuration intConf = cmdLnParser.getInterpreterConfiguration();

			SemanticVerifier.Configuration semVerConfig =
				new SemanticVerifier.Configuration( intConf.executionTarget() );
			semVerConfig.setCheckForMain( false );

			Program program = ParsingUtils.parseProgram(
				intConf.inputStream(),
				intConf.programFilepath().toURI(),
				intConf.charset(),
				intConf.includePaths(),
				intConf.packagePaths(),
				intConf.jolieClassLoader(),
				intConf.constants(),
				semVerConfig,
				INCLUDE_DOCUMENTATION );

            // Perform disembedding if disembed argument are given.
            MonolithDisembedder md = null;
            HashMap<String, ArrayList<String>> dependsOn = null;
            if (!disembedConfig.toString().equals("")){
                md = new MonolithDisembedder(program, configPath, disembedConfig.toString());
                md.DisembedAndMakeProgramDockerReady();
                program = md.p;
                dependsOn = MonolithDisembedder.dependsOn;
            }
            
            // If the visualize arg is true, create the DOT file and terminate.
            if (!visualize.toString().equals("")){
                Visualizer visualizer;
                if(md != null && md.visualizeHelper.size() == 0){
                    visualizer = new Visualizer(program, visualize.toString());
                    visualizer.matchEndPoints();
                    visualizer.generateDotFile();
                }
                else{
                    visualizer = new Visualizer(program, visualize.toString(), md.visualizeHelper);
                    visualizer.matchEndPoints();
                    visualizer.generateDotFile();
                }

                System.exit(0);
            }

            /*
            final Scanner scanner = new Scanner(stream, programDirectory.toUri(), null, INCLUDE_DOCUMENTATION);
            final OLParser olParser = new OLParser(scanner, EMPTY_INCLUDE_PATHS, CLASS_LOADER);
            final Program program = olParser.parse(); */

            final Slicer slicer = Slicer.create(
                    program,
                    configPath,
                    outputDirectory,
                    dependsOn);

            slicer.generateServiceDirectories();

        } catch ( ParserException | InvalidConfigurationFileException | CodeCheckingException | ModuleException | CommandLineException | IOException e ) {
            throw new FaultException( e.getClass().getSimpleName(), e );
        }
    }
}
