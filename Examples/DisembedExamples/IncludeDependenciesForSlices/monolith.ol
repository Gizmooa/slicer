from math import Math
from string_utils import StringUtils
from console import Console
from database import Database

type createUserRequest: void {
    email: string
    username: string
    password: string
}

type loginRequest: void {
    email: string 
    password: string
}

interface UserInterface {
    RequestResponse:
        createUser( createUserRequest )( undefined ),
        login( loginRequest )( undefined ),
        viewUsers( void )( undefined )
}

interface GatewayInterface {
    RequestResponse:
        createUser( createUserRequest )( undefined ),
        login( loginRequest )( undefined ),
        viewUsers( void )( undefined )
}

service UserService {

    execution{ concurrent }
    embed Database as Database
    embed Console as Console
    embed StringUtils as StringUtils
    embed Math as Math

    inputPort UserServer {
        Location: "socket://localhost:8000/"
        Protocol: http { .format = "json" }
        Interfaces: UserInterface
    }

    init
    {
        with ( connectionInfo ) {
            .username = "sa";
            .password = "";
            .host = "";
            .database = "file:userdb/userdb"; // "." for memory-only
            .driver = "hsqldb_embedded"
        };
        connect@Database( connectionInfo )( );
        println@Console( "connected" )( );

        scope ( createTable ) {
            install ( SQLException => println@Console("Userdb table already there" )( ));
            update@Database(
                "create table UserTable(id integer generated always as identity, " +
                "sessionID varchar(255) not null,password varchar(255) not null, " +
                "username varchar(255) not null, email varchar(255) not null, primary key(id))"
            )( ret )
        }
    }
    // END OF INIT

    main
    {
        // http://localhost:8000/viewUsers
        [ viewUsers( )( response ) {
            query@Database(
                "select * from UserTable"
            )( sqlResponse );
            response.values -> sqlResponse.row
        } ]
        // http://localhost:8000/createUser?email=heyo&username=Use3232e&password=123ssword
        [ createUser( request )( response ) {
            random@Math( )( randomResult )
            update@Database(
                "insert into UserTable(username, password, email, sessionID) values (:username, :password, :email, :sessionID)" {
                    .username = request.username,
                    .password = request.password,
                    .email = request.email,
                    // RANDOM GENERATE THIS
                    .sessionID = randomResult
                }
            )( response.status )
        } ]
        // http://localhost:8000/login?email=ImAnEmail&password=Password
        [ login( request )( response ) {
            query@Database(
                "SELECT * FROM UserTable WHERE email=:email AND password=:password" {
                    .email = request.email,
                    .password = request.password
                }
            )(sqlResponse);
            // We need a check here, to check if there is more
            // If there is, there have been generated a non-unique number
            if (#sqlResponse.row > 1) {
                response -> sqlResponse.row[0].SESSIONID
            }
        } ]
    }
}

service GatewayService {
    execution{ concurrent }

    embed UserService as UserServer

    inputPort GatewayServer {
        Location: "socket://localhost:8200/"
        Protocol: http { .format = "json" }
        Interfaces: GatewayInterface
    }

    main {
    [ viewUsers( )( response ) {
        viewUsers@UserServer( )( response )
    } ]
    // http://localhost:8000/createUser?email=heyo&username=Use3232e&password=123ssword
    [ createUser( request )( response ) {
        createUser@UserServer( request )( response )
    } ]
    // http://localhost:8000/login?email=ImAnEmail&password=Password
    [ login( request )( response ) {
        login@UserServer( request )( response )
    } ]

    }

}
