// Main embeds Greeter using a local channel. 
// Main accepts greeting requests at http://localhost:8082
// Greeter accepts greeting requests also at http://localhost:8080 
// talk directly to Gretter
//  # curl http://localhost:8080/greet?name=Client
// talk to Main which forwards to Greeter
//  # curl http://localhost:8082/greet?name=Client

from console import Console

// Some data types
type addRequest { msg:string }
type addResponse { msg:string }

type addRequest2 { msg:string }
type addResponse2 { msg:string }

// Define the API that we are going to publish
interface Service1API {
    RequestResponse: addToString( addRequest )( addResponse )
}

interface Service2API {
    RequestResponse: addToString( addRequest )( addResponse )
}

interface Service3API {
    RequestResponse: addToString( addRequest2 )( addResponse2 )
}

service Service1 {
    execution: sequential

    embed Service2 as S2
    embed Console as Console
    embed Service3 as S3
    
    inputPort MainInput {
        location: "socket://localhost:8082"        // Use TCP/IP
        protocol: http { format = "json" }        // Use HTTP
        interfaces: Service1API                    // Publish GreeterAPI
    }

    init {
    	addToString@S2({msg = "S1"})(response);
    	addToString@S3({msg = "S1"})(response2);
        println@Console(response.msg)()
        println@Console(response2.msg)()
    }

    main{
        addToString( request )( response ) {
            response.msg = "S2: I've received your request " + request.msg
        }
    }
}

service Service2 {
    execution: concurrent // Handle clients concurrently

    embed Console as Console

    inputPort Service2 {
            location: "local"        			// Use TCP/IP
            protocol: http { format = "json" }        // Use HTTP
            interfaces: Service2API                    // Publish GreeterAPI
    }

    // Implementation (the behaviour)
    main {
        /*
        This statement receives a request for greet,
        runs the code in { ... }, and sends response
        back to the client.
        */
        addToString( request )( response ) {
            response.msg = "S2: I've received your request " + request.msg
        }
    }
}

service Service3 {
    execution: sequential

    embed Console as Console
    
    inputPort Service3 {
        location: "local"        		    // Use TCP/IP
        protocol: http { format = "json" }        // Use HTTP
        interfaces: Service3API                    // Publish GreeterAPI
    }
    
    main{
        addToString( request )( response ) {
            response.msg = "S3: I've received your request " + request.msg
        }
    }
}
