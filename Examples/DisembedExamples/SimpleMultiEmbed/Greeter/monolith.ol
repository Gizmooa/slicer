// Main embeds Greeter using a local channel. 
// Main accepts greeting requests at http://localhost:8082
// Greeter accepts greeting requests also at http://localhost:8080 
// talk directly to Gretter
//  # curl http://localhost:8080/greet?name=Client
// talk to Main which forwards to Greeter
//  # curl http://localhost:8082/greet?name=Client

from console import Console

// Some data types
type GreetRequest { name:string }
type GreetResponse { greeting:string }

// Define the API that we are going to publish
interface GreeterAPI {
    RequestResponse: greet( GreetRequest )( GreetResponse )
}

service Main {
    execution: sequential

    embed Greeter as G
    embed Console as Console
    embed SecondMain as SM
    
    inputPort MainInput {
        location: "socket://localhost:8082"        // Use TCP/IP
        protocol: http { format = "json" }        // Use HTTP
        interfaces: GreeterAPI                    // Publish GreeterAPI
    }

    init {
        println@Console("Main:\n  G {\n    location: '"  + G.location + "'\n    protocol: '"  + G.protocol + "'\n    interfaces: '" + G.interfaces + "'\n  }")()
        greet@G({name="Main"})(response);
        println@Console("Main: '"  + response.greeting + "'")()
    }

    main{
        greet( request )( response ) {
            //response.greeting = "Hello, " + request.name + ". I'm Main."
            println@Console("Main: forwarding '" + request.name + "' to G.")()
            greet@G(request)(response)
        }
    }
}

service Greeter {
    execution: concurrent // Handle clients concurrently

    embed Console as Console

    inputPort GreeterInputLocal {
            location: "local"        // Use TCP/IP
            protocol: http { format = "json" }        // Use HTTP
            interfaces: GreeterAPI                    // Publish GreeterAPI
    }

    // Implementation (the behaviour)
    main {
        /*
        This statement receives a request for greet,
        runs the code in { ... }, and sends response
        back to the client.
        */
        greet( request )( response ) {
            println@Console("Greeter: greeting '" + request.name + "'.")()
            response.greeting = "Hello, " + request.name + ". I'm Greeter."
        }
    }
}

service SecondMain {
    execution: sequential

    embed Console as Console
    embed Greeter as Greet
    
    inputPort SecondMainInput {
        location: "socket://localhost:8100"        // Use TCP/IP
        protocol: http { format = "json" }        // Use HTTP
        interfaces: GreeterAPI                    // Publish GreeterAPI
    }

    init {
        println@Console("SecondMain:\n  G {\n    location: '"  + Greet.location + "'\n    protocol: '"  + Greet.protocol + "'\n    interfaces: '" + Greet.interfaces + "'\n  }")()
        greet@Greet({name="SecondMain"})(response);
        println@Console("SecondMain: '"  + response.greeting + "'")()
    }

    main{
        greet( request )( response ) {
            //response.greeting = "Hello, " + request.name + ". I'm Main."
            println@Console("SecondMain: forwarding '" + request.name + "' to G.")()
            greet@Greet(request)(response)
        }
    }
}
