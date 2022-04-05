from string_utils import StringUtils
from console import Console
from database import Database

type anyRequest: any { ? }

type eventResponse: any { ? }

interface CSMInterface {
    RequestResponse:
        viewAllChargingStations( void )( eventResponse ),
        createChargeStation( anyRequest )( eventResponse ),
        retrieveChargeStation( anyRequest )( eventResponse ),
        deleteChargingStation( anyRequest )( eventResponse ),
        retrieveChargeStationFliterPlugType( anyRequest )( eventResponse ),
        retrieveChargeStationFilterParkingSpaceSize( anyRequest )( eventResponse ),
        retrieveChargeStationFilterName( anyRequest )( eventResponse ),
        deleteChargingStationUserOwned(anyRequest)(eventResponse),
        bookOrUnbookChargeStation(anyRequest)(eventResponse),
        retrieveChargeStationFilterBooked(anyRequest)(eventResponse),
        createUserOwnedChargeStation( anyRequest )( eventResponse )
}

interface EDAInterface {
    RequestResponse:
        getEnvDataOnID( anyRequest )( eventResponse )
}

interface CSSharingInterface {
    RequestResponse:
        shareOwnChargeStation( anyRequest )( eventResponse ),
        deleteOwnChargeStation( anyRequest )( eventResponse )
}

interface CSSInterface {
    RequestResponse:
        SearchRetrieveAll(anyRequest)(eventResponse),
        SearchRetrieveByID(anyRequest)(eventResponse),
        SearchFilterPlugType( anyRequest )( eventResponse ),
        SearchFilterParkingSpaceSize( anyRequest )( eventResponse ),
        SearchFilterName( anyRequest )( eventResponse ),
        SearchFilterBooked(anyRequest)(eventResponse)
}

interface BMInterface {
    RequestResponse:
        BMbookOrUnbookParkingSpot( anyRequest )( eventResponse )
}

interface EBInterface {
    RequestResponse:
        publishEvent(anyRequest)(eventResponse)
}


interface GatewayInterface {
    RequestResponse:
        SearchRetrieveAll(anyRequest)(eventResponse),
        SearchRetrieveByID(anyRequest)(eventResponse),
        SearchFilterPlugType( anyRequest )( eventResponse ),
        SearchFilterParkingSpaceSize( anyRequest )( eventResponse ),
        SearchFilterName( anyRequest )( eventResponse ),
        SearchFilterBooked(anyRequest)(eventResponse),
        shareOwnChargeStation( anyRequest )( eventResponse ),
        deleteOwnChargeStation( anyRequest )( eventResponse ),
        bookOrUnbookParkingSpot( anyRequest )( eventResponse ),
        getEnvDataOnID( anyRequest )( eventResponse ),
        createChargeStation( anyRequest )( eventResponse ),
        deleteChargingStation( anyRequest )( eventResponse )
        
}

service EventBus {

    execution{ concurrent }
    embed BookingManagement as BMServer
    embed ChargingStationSearch as CSSServer
    embed ChargingStationManagement as CSMServer
    embed ChargingStationSharing as CSSharingServer
    embed EnvironmentalDataAnalysis as EDAServer

    inputPort EBServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    inputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    outputPort GatewayServer {
        Location: "socket://localhost:8100/"
        Protocol: http { .format = "json" }
        Interfaces: GatewayInterface
    }

    main
    {
        [ publishEvent( request )( response ) {
            // events from ChargeStationSearch
            if (request.event == "viewAllChargingStations") {
                viewAllChargingStations@CSMServer()(response)
            }
            else if (request.event == "retrieveChargeStation") {
                retrieveChargeStation@CSMServer(request)(response)
            }

            else if (request.event == "retrieveChargeStationFliterPlugType") {
                retrieveChargeStationFliterPlugType@CSMServer(request)(response)
            }

            else if (request.event == "retrieveChargeStationFilterParkingSpaceSize") {
                retrieveChargeStationFilterParkingSpaceSize@CSMServer(request)(response)
            }

            else if (request.event == "retrieveChargeStationFilterName") {
                retrieveChargeStationFilterName@CSMServer(request)(response)
            }

            else if (request.event == "retrieveChargeStationFilterBooked") {
                retrieveChargeStationFilterBooked@CSMServer(request)(response)
            }

            // Events from ChargeStationSharing
            else if (request.event == "createUserOwnedChargeStation") {
                createUserOwnedChargeStation@CSMServer(request)(response)
            }

            else if (request.event == "deleteChargingStationUserOwned") {
                deleteChargingStationUserOwned@CSMServer(request)(response)
            }

            // Events from gateway
            // gateway -> ChargeStationSearch
            else if (request.event == "SearchRetrieveAll") {
                SearchRetrieveAll@CSSServer(request)(response)
            }

            else if (request.event == "SearchRetrieveByID") {
                SearchRetrieveByID@CSSServer(request)(response)
            }

            else if (request.event == "SearchFilterPlugType") {
                SearchFilterPlugType@CSSServer(request)(response)
            }
            
            else if (request.event == "SearchFilterParkingSpaceSize") {
                SearchFilterParkingSpaceSize@CSSServer(request)(response)
            }
            
            else if (request.event == "SearchFilterName") {
                SearchFilterName@CSSServer(request)(response)
            }

            else if (request.event == "SearchFilterBooked") {
                SearchFilterBooked@CSSServer(request)(response)
            }

            // gateway -> ChargeStationShare
            else if (request.event == "shareOwnChargeStation") {
                shareOwnChargeStation@CSSharingServer(request)(response)
            }

            else if (request.event == "deleteOwnChargeStation") {
                deleteOwnChargeStation@CSSharingServer(request)(response)
            }

            // gateway -> ChargeStationManagement
            else if (request.event == "bookOrUnbookParkingSpot") {
                bookOrUnbookChargeStation@CSMServer(request)(response)
            }

            else if (request.event == "createChargeStation") {
                createChargeStation@CSMServer(request)(response)
            }

            else if (request.event == "deleteChargingStation") {
                deleteChargingStation@CSMServer(request)(response)
            }

            // gateway -> environmentalDataAnalysis
            else if (request.event == "getEnvDataOnID") {
                getEnvDataOnID@EDAServer(request)(response)
            }
            
        } ]
    }
}

service BookingManagement {

    execution{ concurrent }

    inputPort BMServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: BMInterface
    }

    inputPort BMServer {
        Location: "socket://localhost:8003/"
        Protocol: http { .format = "json" }
        Interfaces: BMInterface
    }

    outputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    main
    {
        // http://localhost:8000/viewUsers
        [ BMbookOrUnbookParkingSpot( request )( response ) {
            publishEvent@EBServer({event="bookOrUnbookChargeStation", booked=request.booked, id = request.id })(response)
        } ]
    }
}

service ChargingStationSearch {

    execution{ concurrent }

    inputPort CSSServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: CSSInterface
    }

    inputPort CSSServer {
        Location: "socket://localhost:8001/"
        Protocol: http { .format = "json" }
        Interfaces: CSSInterface
    }

    outputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    main
    {
        [ SearchRetrieveAll( )( response ) {
            publishEvent@EBServer({event="viewAllChargingStations"})(response)
        } ]
        [ SearchRetrieveByID( request )( response ) {
            publishEvent@EBServer({event="retrieveChargeStation", id=request.id})(response)
        } ]
        [ SearchFilterPlugType(request  )( response ) {
            publishEvent@EBServer({event="retrieveChargeStationFliterPlugType", plugType = request.plugType})(response)
        } ]
        [ SearchFilterParkingSpaceSize( request )( response ) {
            publishEvent@EBServer({event="retrieveChargeStationFilterParkingSpaceSize", parkingSpaceSize = request.parkingSpaceSize})(response)
        } ]
        [ SearchFilterName( request )( response ) {
            publishEvent@EBServer({event="retrieveChargeStationFilterName", name = request.name})(response)
        } ]
        [ SearchFilterBooked( request )( response ) {
            publishEvent@EBServer({event="retrieveChargeStationFilterBooked", booked=request.booked})(response)
        } ]
    }
}

service ChargingStationSharing {

    execution{ concurrent }

    inputPort CSSharingServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: CSSharingInterface
    }

    inputPort CSSharingServer {
        Location: "socket://localhost:8002/"
        Protocol: http { .format = "json" }
        Interfaces: CSSharingInterface
    }

    outputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    main
    {
        [ shareOwnChargeStation( request )( response ) {
            publishEvent@EBServer({event="createUserOwnedChargeStation", name = request.name, 
                                plugType = request.plugType, chargingType = request.chargingType, 
                                parkingSpaceSize = request.parkingSpaceSize, userOwned = request.userOwned})(response)
        } ]
        [ deleteOwnChargeStation( request )( response ) {
            publishEvent@EBServer({event="deleteChargingStationUserOwned", id = request.id})(response)
        } ]
    }
}

service EnvironmentalDataAnalysis {

    execution{ concurrent }

    inputPort EDAServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: EDAInterface
    }

    inputPort EDAServer {
        Location: "socket://localhost:8004/"
        Protocol: http { .format = "json" }
        Interfaces: EDAInterface
    }

    outputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    main
    {
        // http://localhost:8000/viewUsers
        [ getEnvDataOnID( )( response ) {
            response.data = 42
        } ]
    }
}

service ChargingStationManagement {

    execution{ concurrent }
    embed Database as Database
    embed Console as Console
    embed StringUtils as StringUtils

    inputPort CSSharingServerLocal {
        Location: "local"
        Protocol: http { .format = "json" }
        Interfaces: CSMInterface
    }

    inputPort CSSharingServer {
        Location: "socket://localhost:8000/"
        Protocol: http { .format = "json" }
        Interfaces: CSMInterface
    }
    
    outputPort EBServer {
        Location: "socket://localhost:5672/"
        Protocol: http { .format = "json" }
        Interfaces: EBInterface
    }

    init
    {
        with ( connectionInfo ) {
            .username = "sa";
            .password = "";
            .host = "";
            .database = "file:csmtable/csmtable"; // "." for memory-only
            .driver = "hsqldb_embedded"
        };
        connect@Database( connectionInfo )( );
        println@Console( "connected" )( );

        scope ( createTable ) {
            install ( SQLException => println@Console("CSMTable table already there" )( ));
            // Booked=0 not booked, anything larger than 0 is the user ID of the booker
            // userOwned=0 not user booked, anything larger than 0 is the user ID of the owner
            update@Database(
                "create table CSMTable(id integer generated always as identity, " +
                "name varchar(255) not null, plugType varchar(255) not null, " +
                "chargingType varchar(255) not null, parkingSpaceSize integer not null, booked integer not null, userOwned integer not null, primary key(id))"
            )( ret )
        }
        // Fill up the database with entries "fetched" from infrastructures
        update@Database(
            "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                .name = "sdu",
                .plugType = "eu",
                .chargingType = "fast",
                .parkingSpaceSize = 123,
                .booked = 0,
                .userOwned = 0
            }
        )( response.status )

        update@Database(
            "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                .name = "nyborgvej 42",
                .plugType = "eu",
                .chargingType = "fast",
                .parkingSpaceSize = 10,
                .booked = 0,
                .userOwned = 0
            }
        )( response.status )

        update@Database(
            "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                .name = "svendborgvej 101",
                .plugType = "us",
                .chargingType = "slow",
                .parkingSpaceSize = 123,
                .booked = 0,
                .userOwned = 0
            }
        )( response.status )

        update@Database(
            "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                .name = "svendborgvej 202",
                .plugType = "us",
                .chargingType = "fast",
                .parkingSpaceSize = 123,
                .booked = 0,
                .userOwned = 0
            }
        )( response.status )
    }
    // END OF INIT

    main
    {
        // http://localhost:8000/viewUsers
        [ viewAllChargingStations( )( response ) {
            query@Database(
                "select * from CSMTable"
            )( sqlResponse );
            response.values -> sqlResponse.row
        } ]
        // http://localhost:8000/createUser?email=heyo&username=Use3232e&password=123ssword
        [ createChargeStation( request )( response ) {
            update@Database(
                "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                    .name = request.name,
                    .plugType = request.plugType,
                    .chargingType = request.chargingType,
                    .parkingSpaceSize = request.parkingSpaceSize,
                    .booked = 0,
                    .userOwned = 0
                }
            )( response.status )
        } ]
        [ createUserOwnedChargeStation( request )( response ) {
            update@Database(
                "insert into CSMTable(name, plugType, chargingType, parkingSpaceSize, booked, userOwned) values (:name, :plugType, :chargingType, :parkingSpaceSize, :booked, :userOwned)" {
                    .name = request.name,
                    .plugType = request.plugType,
                    .chargingType = request.chargingType,
                    .parkingSpaceSize = request.parkingSpaceSize,
                    .booked = 0,
                    .userOwned = 0
                }
            )( response.status )
        } ]
        [ retrieveChargeStation( request )( response ) {
            query@Database(
            "select * from CSMTable where id=:id" {
                .id = request.id
            }
            )(sqlResponse);
            if (#sqlResponse.row == 1) {
                response.values -> sqlResponse.row[0]
            }
        } ]
        [ deleteChargingStation(request)(response) {
        update@Database(
            "delete from CSMTable where id=:id" {
                .id = request.id
            }
            )(response.status)
        } ]
        [ deleteChargingStationUserOwned(request)(response) {
        update@Database(
            "delete from CSMTable where id=:id and userOwned != 0" {
                .id = request.id
            }
            )(response.status)
        } ]
        [ retrieveChargeStationFliterPlugType( request )( response ) {
                query@Database(
                "select * from CSMTable where plugType=:plugType" {
                    .plugType = request.plugType
                }
                )(sqlResponse);
                response.values -> sqlResponse.row
            } ]
        [ retrieveChargeStationFilterParkingSpaceSize( request )( response ) {
                query@Database(
                "select * from CSMTable where parkingSpaceSize = :parkingSpaceSize" {
                    .parkingSpaceSize = request.parkingSpaceSize
                }
                )(sqlResponse);
                response.values -> sqlResponse.row
            } ]
        [ retrieveChargeStationFilterName( request )( response ) {
                query@Database(
                "select * from CSMTable where name=:name" {
                    .name = request.name
                }
                )(sqlResponse);
                response.values -> sqlResponse.row
            } ]
        [ retrieveChargeStationFilterBooked( request )( response ) {
                query@Database(
                "select * from CSMTable where booked=:booked" {
                    .booked = request.booked
                }
                )( sqlResponse );
                response.values -> sqlResponse.row
        } ]
        [ bookOrUnbookChargeStation(request)(response) {
            update@Database(
                "update CSMTable set booked=:booked where id=:id" {
                    .booked = request.booked,
                    .id = request.id
                }
            )(response.status)
        } ]
    }
}


service Gateway {

    execution{ concurrent }
    embed EventBus as EB

    inputPort GatewayServer {
        Location: "socket://localhost:8100/"
        Protocol: http { .format = "json" }
        Interfaces: GatewayInterface
    }
    // END OF INIT

    main
    {
        // ChargeStationSearch Service
        [ SearchRetrieveAll( request )( response ) {
            publishEvent@EB({event="SearchRetrieveAll"})(response)
        } ]
        [ SearchRetrieveByID( request )( response ) {
            publishEvent@EB({event="SearchRetrieveByID", id=request.id})(response)
        } ]
        [ SearchFilterPlugType(request )( response ) {
            publishEvent@EB({event="SearchFilterPlugType", plugType = request.plugType})(response)
        } ]
        [ SearchFilterParkingSpaceSize(request )( response ) {
            publishEvent@EB({event="SearchFilterParkingSpaceSize", parkingSpaceSize = request.parkingSpaceSize})(response)
        } ]
        [ SearchFilterName(request )( response ) {
            publishEvent@EB({event="SearchFilterName", name = request.name})(response)
        } ]
        [ SearchFilterBooked(request )( response ) {
            publishEvent@EB({event="SearchFilterBooked", booked=request.booked})(response)
        } ]
        // ChargestationShare service
        //curl "http://localhost:8100/shareOwnChargeStation?name=sduu&plugType=fast&chargingType=eu&parkingSpaceSize=321&userOwned=101"

        [ shareOwnChargeStation(request )( response ) {
            publishEvent@EB({event="shareOwnChargeStation", name = request.name, 
                                plugType = request.plugType, chargingType = request.chargingType, 
                                parkingSpaceSize = request.parkingSpaceSize, userOwned = request.userOwned})(response)
        } ]
        [ deleteOwnChargeStation(request )( response ) {
            publishEvent@EB({event="deleteOwnChargeStation", id = request.id})(response)
        } ]
        // ChargeStationManagement Service
        [ bookOrUnbookParkingSpot(request )( response ) {
            publishEvent@EB({event="bookOrUnbookParkingSpot", id = request.id, booked = request.booked})(response)
        } ]
        // curl "http://localhost:8100/createChargeStation?name=sduu&plugType=fast&chargingType=eu&parkingSpaceSize=321"
        [ createChargeStation(request )( response ) {
            publishEvent@EB({event="createChargeStation", name = request.name, 
                                plugType = request.plugType, chargingType = request.chargingType, 
                                parkingSpaceSize = request.parkingSpaceSize})(response)
        } ]
        [ deleteChargingStation(request )( response ) {
            publishEvent@EB({event="deleteChargingStation", id = request.id})(response)
        } ]
        // EnvironmentalData Service
        [ getEnvDataOnID(request )( response ) {
            publishEvent@EB({event="getEnvDataOnID"})(response)
        } ]
    }
}
