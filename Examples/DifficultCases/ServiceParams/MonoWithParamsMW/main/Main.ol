from console import Console
type GreetRequest : void {
	name : string
}
type GreetResponse : void {
	greeting : string
}
interface GreeterAPI {
	RequestResponse:
		greet( GreetRequest )( GreetResponse )
}

type InitializeRequest : void {
	param1 : string
}
type InitializeReponse : void {
	rparam1 : string
}

interface MWAPI {
	RequestResponse:
		initializeServiceParams( InitializeRequest )( InitializeReponse )
}

service MW {
	execution: sequential
	embed Console as Console
	//embed SecondMain as SM - TODO - Remove after disembeding. If used, should be replaced by outputPort
	inputPort MWLocation {
		location: "socket://localhost:7980"
		protocol: http{
			format = "json"
		}
		interfaces: MWAPI
	}
    outputPort OtherMW {
		location: "socket://localhost:7979"
		protocol: http{
			format = "json"
		}
		interfaces: MWAPI
	}
	init {
		initializeServiceParams@OtherMW({param1 = "MW Service Param!"})()
	}
}

service Main {
	execution: sequential
	embed Greeter as G
	embed Console as Console
	//embed SecondMain as SM - TODO - Remove after disembeding. If used, should be replaced by outputPort
	inputPort MainInput {
		location: "socket://localhost:8082"
		protocol: http{
			format = "json"
		}
		interfaces: GreeterAPI
	}
	init {
		println@Console( "Main:
  G {
    location: '" + G.location + "'
    protocol: '" + G.protocol + "'
    interfaces: '" + G.interfaces + "'
  }" )(  )
		greet@G( {
			name = "Main"
		} )( response )
		println@Console( "Main: '" + response.greeting + "'" )(  )
	}
	main {
		greet( request )( response ){
			println@Console( "Main: forwarding '" + request.name + "' to G." )(  )
			greet@G( request )( response )
		}
	}
}

// TODO - This service needs to be included by the slicer.
service Greeter {
    execution: concurrent // Handle clients concurrently

    embed Console as Console

    // An input port publishes APIs to clients
    inputPort GreeterInput {
        location: "socket://localhost:8080"        // Use TCP/IP
        protocol: http { format = "json" }        // Use HTTP
        interfaces: GreeterAPI                    // Publish GreeterAPI
    }

    inputPort GreeterInput2 {
        location: "local"
        interfaces: GreeterAPI
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