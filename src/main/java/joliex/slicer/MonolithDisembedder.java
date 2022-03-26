package joliex.slicer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Provider.Service;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;

import jolie.lang.parse.ast.EmbedServiceNode;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.OperationDeclaration;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.ServiceNode;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.OutputPortInfo;
import jolie.util.Pair;
import jolie.lang.parse.ast.expression.ConstantStringExpression;

public class MonolithDisembedder {
    static Program p;
	static Path configPath;
	static String slicesConfigPath;
	static int newPort = 5601;
    // Embeddings: key=Service getting embedded, value=services embedding the key service
	static HashMap<String, ArrayList<String>> embeddings = new HashMap<>();
    static HashMap<String, ArrayList<ServiceNode>> visualizeHelper = new HashMap<>();
    static int amountOfEmbeds = 0;
    static JSONObject slicesConfig = null;
    static HashMap<String, ServiceNode> newServices = new HashMap<>(); 
    static HashMap<OutputPortInfo, ServiceNode> danglingEmbedOPs = new HashMap<>();
    // depends on: Key = service, value = services the key service depends on
    static HashMap<String, ArrayList<String>> dependsOn = new HashMap<>();

    public MonolithDisembedder(Program p, Path configPath, String slicesConfigPath) throws FileNotFoundException {
        MonolithDisembedder.p = p;
        this.configPath = configPath;
		MonolithDisembedder.slicesConfigPath = slicesConfigPath;
        MonolithDisembedder.slicesConfig = (JSONObject) JSONValue.parse( new FileReader( slicesConfigPath ) );
    }


    public void makeProgramDockerReady() throws FileNotFoundException {
        // Generate the embed pointers
        generateEmbedArray();

        // Disembed the embedded service and add proxy service if needed to hashmap
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach(MonolithDisembedder::disembedTheEmbedded);

        // Disembed the embedder, create an OP
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach(MonolithDisembedder::disembedTheEmbedder);

        // If we created a new service because of multi-embed, add it to the program
        for (OLSyntaxNode node : newServices.values()) {
            p.children().add(node);
        }

		// Remove disembedded embed generated OP's
        for (Map.Entry< OutputPortInfo, ServiceNode> entry : danglingEmbedOPs.entrySet()) {
            entry.getValue().program().children().remove(entry.getKey());
        }

		// Fix all output ports.
        // If multiple docker parents for a service we will write the location [Service1 | Service2]:PORT
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach(MonolithDisembedder::fixOutputPorts);

		// Rewrite the config file w.r.t the disembedding
		rewriteConfigFile();
    }

    private static void fixOutputPorts(ServiceNode service){
        service.program()
            .children()
            .stream()
            .filter( OutputPortInfo.class::isInstance )
            .map( OutputPortInfo.class::cast )
            .forEach(op -> {
                if(op.location() != null && op.location().toString().contains("localhost")){
                    matchAndFixOutputPort(service, op);
                }
            });
    }

    // Matches the op of the service with an IP; and fixes the OP corresponding to the IP of the match.
    private static void matchAndFixOutputPort(ServiceNode currentService, OutputPortInfo op){
        HashMap<String, ServiceNode> inputPorts = new HashMap<>();
        ArrayList<String> rootNodes = getRootNodes();

        // Fill the InputPort -> ServiceNode hashmap
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach( service ->
                service
                    .program()
                    .children()
                    .stream()
                    .filter( InputPortInfo.class::isInstance )
                    .map( InputPortInfo.class::cast )
                    .forEach(inputPort -> {
                        inputPorts.put(inputPort.location().toString(), service);
                    }));
        
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach( service ->
                service
                    .program()
                    .children()
                    .stream()
                    .filter( OutputPortInfo.class::isInstance )
                    .map( OutputPortInfo.class::cast )
                    .forEach(outputPort -> {
                        for (String key : inputPorts.keySet()){
                            // Ignore the embed generated OPs
                            if (outputPort.location() != null){
                                if(key.equals(outputPort.location().toString())){
                                    String ipParent = findParent(inputPorts.get(key).name(), rootNodes);
                                    String opParent = findParent(currentService.name(), rootNodes);
                                    // TODO - Maybe add a depends_on dependency here for the op parent
                                    if (dependsOn.containsKey(opParent)){
                                        dependsOn.get(opParent).add(ipParent);
                                    }
                                    else{
                                        ArrayList<String> node = new ArrayList<>();
                                        node.add(ipParent);
                                        dependsOn.put(opParent, node);
                                    }
                                    // If the parents are equal, keep the adress. Otherwise swap localhost with the ip's parent node aka the docker location
                                    if (!ipParent.equals(opParent)){
                                        String newOutputPort = outputPort.location().toString().replace("localhost", ipParent.toLowerCase());
                                        ArrayList<ServiceNode> visualizeHelperList = new ArrayList<>();
                                        visualizeHelperList.add(inputPorts.get(key));
                                        visualizeHelperList.add(currentService);
                                        visualizeHelper.put(newOutputPort, visualizeHelperList);
                                        ConstantStringExpression newLocation = new ConstantStringExpression(outputPort.location().context(), 
                                                                                    newOutputPort);
                                        outputPort.setLocation(newLocation);
                                    }
                                }
                            }
                        }
                    }));
        
    }

    public static String findParent(String serviceName, ArrayList<String> rootNodes){
        if (rootNodes.contains(serviceName)){
            return serviceName;
        }
        if (embeddings.get(serviceName).size() == 1){
            return embeddings.get(serviceName).get(0);
        }
        else{
            String embedString = "[";
            String lastElement = embeddings.get(serviceName).get(embeddings.get(serviceName).size());
            for (String embed : embeddings.get(serviceName)){
                if (embed == lastElement){
                    embedString = embedString + embed + "]";
                }
                else{
                    embedString = embedString + embed + " | ";
                }
            }
            return embedString;
        }
    }

    private static void addNonLocalIPIfNeeded( ServiceNode service, String actualServiceName ){
        // Check if the service should be disembedded
        Set<String> keys = slicesConfig.keySet();
        for (String key : keys){
            ArrayList<String> values = (ArrayList) slicesConfig.get(key);
            // If it should be disembedded, add a non-local ip if the service doesnt own one
            // and replace the local with non-local.
            if (values.contains(actualServiceName)){
                Iterator<InputPortInfo> ipIterator = service.program().children()
                    .stream()
                    .filter( InputPortInfo.class::isInstance )
                    .map( InputPortInfo.class::cast )
                    .iterator();

                // Check if there's a non-local ip
                InputPortInfo currentIP = null;
                while(ipIterator.hasNext()){
                    currentIP = ipIterator.next();
                    // ASSUMPTION: Here I am assuming any address not local, shall be a docker-loc
                    // maybe should be replaced by checking if contains localhost.
                    if (!currentIP.location().toString().equals("local")){
                        return;
                    }
                }
                // We didn't find a non-local ip, create one and replace it with the local ip
                InputPortInfo newNonLocalIP = newDockerInputPort(currentIP);
                int localIPIndex = service.program().children().indexOf(currentIP);
                service.program().children().set(localIPIndex, newNonLocalIP);
            }
        }
    }

    public static InputPortInfo newDockerInputPort(InputPortInfo localIP) {
		String newLocation = "socket://localhost:"+newPort+"";
		newPort = newPort + 1;
		OLSyntaxNode newLocationOb = new ConstantStringExpression(localIP.location().context(), newLocation);
		InputPortInfo newNonLocalIP = new InputPortInfo(localIP.context(), localIP.id()+"NonLocal", newLocationOb, 
														localIP.protocol(), localIP.aggregationList(), localIP.redirectionMap());
        for (InterfaceDefinition id : localIP.getInterfaceList()) {
            newNonLocalIP.addInterface(id);
        }
		return newNonLocalIP;
	}

    private static void disembedTheEmbedder( ServiceNode service ) {
        // Check if the current service are embedding a service that are going to be disembedded
        if (slicesConfig.containsKey(service.name())){
            // Disembed the embedder
            service.program()
            .children()
            .stream()
            .filter( EmbedServiceNode.class::isInstance )
            .map( EmbedServiceNode.class::cast )
            .forEach( embedding -> {
                if(newServices.containsKey(embedding.serviceName()+"By"+service.name())){
                    disembedTheEmbedderMulti(embedding, embedding.serviceName()+"By"+service.name(), service);
                } else
                    try {
                        if (embedShouldBeDisembedded(service, embedding)) {
                            disembedTheEmbedderSingle(embedding, service);

                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } 
            });
        }
    }

    private static Boolean embedShouldBeDisembedded(ServiceNode serviceNode, EmbedServiceNode embed) throws FileNotFoundException {
		JSONObject slicesConfig = (JSONObject) JSONValue.parse( new FileReader( slicesConfigPath ) );
		if (slicesConfig.containsKey(serviceNode.name())){
			Iterator<?> jsonIterator = ((JSONArray) slicesConfig.get(serviceNode.name())).iterator();
			while (jsonIterator.hasNext()) {
				String currentService = (String) jsonIterator.next();
				// If one of the values for the service key are equal to current embed.service name
				// this embed should be disembedded.
				if (currentService.equals(embed.service().name())){
					return true;
				}
			}
		}
		return false;
	}

    private static void disembedTheEmbedderSingle( OLSyntaxNode embed, ServiceNode service) {
        EmbedServiceNode embeddedService = (EmbedServiceNode) embed;
        InputPortInfo embeddedServicesIP = findFirstNonLocalIP(embeddedService.service());
        OutputPortInfo opReplacementForEmbed = createOPFromIP(embeddedServicesIP, (EmbedServiceNode) embed);

        int indexForEmbed = service.program().children().indexOf(embed);
        danglingEmbedOPs.put( embeddedService.bindingPort(), service);
        service.program().children().set(indexForEmbed, opReplacementForEmbed);
    }

    private static void disembedTheEmbedderMulti( OLSyntaxNode embed, String newServiceName, ServiceNode service ) {
        EmbedServiceNode embeddedService = (EmbedServiceNode) embed;
        InputPortInfo embeddedServicesIP = findFirstNonLocalIP(newServices.get(newServiceName));
        OutputPortInfo opReplacementForEmbed = createOPFromIP(embeddedServicesIP, embeddedService);
		// Re-do the location. The function sets it for the embed, but as this OP should point to the copy of
		// the embedded service a new location is required.
		ConstantStringExpression newLocation = new ConstantStringExpression(opReplacementForEmbed.context(), 
			opReplacementForEmbed.location().toString().replace(embeddedService.serviceName().toLowerCase(), newServiceName).toLowerCase());
		
		opReplacementForEmbed.setLocation(newLocation);

        int indexForEmbed = service.program().children().indexOf(embed);
        danglingEmbedOPs.put( embeddedService.bindingPort(), service);
        service.program().children().set(indexForEmbed, opReplacementForEmbed);
    }

    private static InputPortInfo findFirstNonLocalIP ( ServiceNode service ) {
        Iterator<InputPortInfo> ipIterator = service.program().children()
			.stream()
			.filter( InputPortInfo.class::isInstance )
			.map( InputPortInfo.class::cast )
			.iterator();

        // Check if there's a non-local ip
        while(ipIterator.hasNext()){
            InputPortInfo currentIP = ipIterator.next();
            if (!currentIP.location().toString().equals("local")){
                return currentIP;
            }
        }
        return null;
    }

    private static OutputPortInfo createOPFromIP(InputPortInfo nonLocalIP, EmbedServiceNode embed) {
		OutputPortInfo opReplacementForEmbed = new OutputPortInfo(embed.context(), embed.bindingPort().id());
		for (InterfaceDefinition interDef : nonLocalIP.getInterfaceList()){
			opReplacementForEmbed.addInterface(interDef);
		}
		for (OperationDeclaration opDec : nonLocalIP.operations()) {
			opReplacementForEmbed.addOperation(opDec);
		}
		ConstantStringExpression newLocation = new ConstantStringExpression(nonLocalIP.context(), 
																			nonLocalIP.location().toString().replace("localhost", embed.serviceName().toLowerCase()));

		opReplacementForEmbed.setLocation(newLocation);
		opReplacementForEmbed.setProtocol(nonLocalIP.protocol());
		return opReplacementForEmbed;
	}

    private static void disembedTheEmbedded( ServiceNode service ) {
        Set<String> keys = slicesConfig.keySet();
        for (String key : keys){
            ArrayList<String> values = (ArrayList) slicesConfig.get(key);
            if (values.contains(service.name())){
                // Disembed the embedded
                int amountOfEmbeds = embeddings.containsKey(service.name()) ? embeddings.get(service.name()).size() : 0;
                if (amountOfEmbeds > 1) {
                    disembedTheEmbeddedMulti(service, key);
                    // The sliced service will now be a dependency before the embedder can start in docker
                    if (dependsOn.containsKey(key)){
                        dependsOn.get(key).add(service.name()+"By"+key);
                    }
                    else{
                        ArrayList<String> node = new ArrayList<>();
                        node.add(service.name()+"By"+key);
                        dependsOn.put(key, node);
                    }
                    // Remove the embed pointer towards the service
                    ArrayList<String> embedList = embeddings.get(service.name());
                    embedList.remove(key);
                    embeddings.put(service.name(), embedList);
                }
				else{
                    // As we are disembedding, if the service doesn't have a non-local adress, add it.
                    addNonLocalIPIfNeeded(service, service.name());
                    // The sliced service will now be a dependency before the embedder can start in docker
                    if (dependsOn.containsKey(key)){
                        dependsOn.get(key).add(service.name());
                    }
                    else{
                        ArrayList<String> node = new ArrayList<>();
                        node.add(service.name());
                        dependsOn.put(key, node);
                    }
                    // Remove the embed pointer towards the service
					ArrayList<String> embedList = embeddings.get(service.name());
                    embedList.remove(key);
                    embeddings.put(service.name(), embedList);
				}
            }
        }
    }

    private static void disembedTheEmbeddedMulti( ServiceNode service, String parent ) {
        Pair< String, TypeDefinition > newServiceParamPair = null;
        if (service.parameterConfiguration().isPresent()){
            newServiceParamPair = new Pair<String,TypeDefinition>(service.parameterConfiguration().get().variablePath(), 
                                                                service.parameterConfiguration().get().type());
        }
        List<OLSyntaxNode> newList = new ArrayList<>();
        newList.addAll(service.program().children());
        Program newProg = new Program(service.context(), newList);
        ServiceNode newServiceNode = ServiceNode.create(service.context(), service.name()+"By"+parent,
                                                        service.accessModifier(), newProg, newServiceParamPair);
        // As we are disembedding, if the service doesn't have a non-local adress, add it.
        addNonLocalIPIfNeeded(newServiceNode, service.name());
        newServices.put(service.name()+"By"+parent, newServiceNode);
    }

    private static void generateEmbedArray() {
        for( OLSyntaxNode n : p.children() ) {
			if( n instanceof ServiceNode ) {
				ServiceNode serviceNode = (ServiceNode) n;
				for (OLSyntaxNode j : serviceNode.program().children()) {
					if (j instanceof EmbedServiceNode) {
						EmbedServiceNode embed = (EmbedServiceNode) j;
						if (embeddings.containsKey(embed.serviceName())){
							ArrayList<String> embedList = embeddings.get(embed.serviceName());
							embedList.add(serviceNode.name());
							embeddings.put(embed.serviceName(), embedList);
						}
						else {
							ArrayList<String> embedList = new ArrayList<>();
							embedList.add(serviceNode.name());
							embeddings.put(embed.serviceName(), embedList);
						}
						
					}
				}
			}
		}
    }

    public static ArrayList<String> getRootNodes(){
		ArrayList<String> rootNodes = new ArrayList<>();
        for (OLSyntaxNode child : p.children()) {
			if (child instanceof ServiceNode) {
				ServiceNode serviceNode = (ServiceNode) child;
				// Is this service a root node, if yes add it to the list of root node's
				if (!embeddings.containsKey(serviceNode.name())){
					rootNodes.add(serviceNode.name());
				}
				else {
					if (embeddings.get(serviceNode.name()).size() == 0) {
						rootNodes.add(serviceNode.name());
					}
				}
			}
		}
        return rootNodes;
    }

	private static void rewriteConfigFile() throws FileNotFoundException {
		ArrayList<String> rootNodes = getRootNodes();
		JSONObject configJSON = (JSONObject) JSONValue.parse( new FileReader( configPath.toString() ) );
		JSONObject config = new JSONObject();

        // Add the root node's to the config, if they were already defined in the config 
        // copy the values and use them. Otherwise use a location filler.
		for (String rootNode : rootNodes) {
			if (configJSON != null && configJSON.containsKey(rootNode)){
				config.put(rootNode, configJSON.get(rootNode));
			}
			else{
				JSONObject locationFiller = new JSONObject();
				locationFiller.put("location", "");
				config.put(rootNode, locationFiller);
			}
		}

		// Delete old config
		File myObj = new File(configPath.toString()); 
		if (myObj.delete()) { 
		} else {
		    System.out.println("Failed to delete the file.");
		} 

		// Write new config
		try (FileWriter file = new FileWriter(configPath.toString())) {
            file.write(config.toJSONString());
        } catch (IOException e) {
            e.printStackTrace();
        }

	}

}
