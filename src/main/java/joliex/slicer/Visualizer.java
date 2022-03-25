package joliex.slicer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import jolie.lang.parse.ast.EmbedServiceNode;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.ServiceNode;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.OutputPortInfo;

public class Visualizer {
    static Program p;
    String dotFileName;
    static List<Edge> EmbedEdges = new ArrayList<>();
    static List<ConnectivityEdge> ConnectivityEdges = new ArrayList<>();
    public Visualizer(Program p, String dotFileName){
        Visualizer.p = p;
        this.dotFileName = dotFileName;
    }

    // Used to match endpoints, aka IP and OP of services
    public void matchEndPoints(){
        HashMap<String, ServiceNode> inputPorts = new HashMap<>();

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
                    .forEach(inputPort -> inputPorts.put(inputPort.location().toString().replaceAll("localhost", service.name().toLowerCase()), service)));
        
        // Fill the list of connectivity edges
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
                                    ConnectivityEdges.add(new ConnectivityEdge(service, inputPorts.get(key), key));
                                }
                            }
                        }
                    }));

        // Fill the list of embed edges
        p.children()
            .stream()
            .filter( ServiceNode.class::isInstance )
            .map( ServiceNode.class::cast )
            .forEach( service ->
                service
                    .program()
                    .children()
                    .stream()
                    .filter( EmbedServiceNode.class::isInstance )
                    .map( EmbedServiceNode.class::cast )
                    .forEach(embed -> EmbedEdges.add(new Edge(service, embed.service()))));
    }

    public void generateDotFile(){
        // Create the DOT file.
        createDotFile();

        try{
        FileWriter myWriter = new FileWriter(dotFileName);
        myWriter.write("digraph G {" + "\n");

        // Draw embed edges
        for (Edge edge : EmbedEdges) {
            myWriter.write("\t" + edge.getFromNode().name() + " -> " + edge.getToNode().name() + "; \n");
        }

        // Draw connectivity edges with a blue color
        for (ConnectivityEdge edge : ConnectivityEdges){
            myWriter.write("\t" + edge.getFromNode().name() + " -> " + edge.getToNode().name() +
                        "[ label= \"" + edge.getLocation() + "\" color=\"blue\"];" + "\n");
        }
        myWriter.write("}" + "\n");
        myWriter.close();

        } catch (IOException e) {
            System.out.println("An error occurred during dot file generation. ");
            e.printStackTrace();
        }
    }

    private void createDotFile(){
        try {
            File myObj = new File(dotFileName);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists. Deleting and creating it from new. ");
                if (myObj.delete()) { 
                    myObj.createNewFile();
                } else {
                    System.out.println("Failed to delete the file.");
                } 
            }
        } catch (IOException e) {
            System.out.println("An error occurred during creation of dot file.");
            e.printStackTrace();
        }
    }

    // Datastructures:
    private class Edge {
        private final ServiceNode from;
        private final ServiceNode to;
        public Edge(ServiceNode from, ServiceNode to) {
            this.from = from;
            this.to = to;
        }
        public ServiceNode getFromNode() {
            return from;
        }

        public ServiceNode getToNode() {
            return to;
        }
    }
    private class ConnectivityEdge extends Edge {
        private final String location;
        public ConnectivityEdge(ServiceNode from, ServiceNode to, 
                                String location){
            super(from, to);
            this.location = location;
        }

        public String getLocation(){
            return location;
        }
    }
}
