type SumRequest: void {
    .term [1,*] : int
}

type SubRequest: void {
    minuend: int 
    subtraend: int
}

type MulRequest: void {
    factor * : double
}

type DivRequest: void {
    dividend: double
    divisor: double
}

interface CalculatorInterface {
    RequestResponse:
        sum( SumRequest )( int ),
        sub( SubRequest )( int ),
        mul( MulRequest )( double ),
        div( DivRequest )( double ) 
}

service CalculatorService {

  execution: concurrent

  inputPort CalculatorPort {
      location: "socket://localhost:8000"
      protocol: http { format = "json" }
      interfaces: CalculatorInterface
  }     

  main {

      [ sum( request )( response ) {
          for( t in request.term ) {
              response = response + t
          }
      }]

      [ sub( request )( response ) {
          response = request.minuend - request.subtraend
      }]

      [ mul( request )( response ) {
          response = 1
          for ( f in request.factor ) {
              response = response * f 
          }
      }]

      [ div( request )( response ) {
          response = request.dividend / request.divisor
      }]
  }

}

// ADVANCED Calculator service
type FactorialRequest: void {
    term: int
}
type FactorialResponse: void {
    factorial: long 
}

type AverageRequest: void {
    term*: int 
}
type AverageResponse: void {
    average: double
}

type PercentageRequest: void {
    term: double
    percentage: double
}
type PercentageResponse: double

interface AdvancedCalculatorInterface {
    RequestResponse:
        factorial( FactorialRequest )( FactorialResponse ),
        average( AverageRequest )( AverageResponse ),
        percentage( PercentageRequest )( PercentageResponse )
}

service AdvancedCalculatorService {

    execution: concurrent

    outputPort Calculator {
         location: "socket://localhost:8000"
         protocol: http { format = "json" }
         interfaces: CalculatorInterface
    }

    inputPort AdvancedCalculatorPort {
         location: "socket://localhost:8001"
         protocol: http { format = "json" }
         interfaces: AdvancedCalculatorInterface
    }

    main {
        [ factorial( request )( response ) {
            for( i = request.term, i > 0, i-- ) {
                req_mul.factor[ #req_mul.factor ] = i
            }
            mul@Calculator( req_mul )( response.factorial )            
        }]

        [ average( request )( response ) {
            sum@Calculator( request )( sum_res )
            div@Calculator( { dividend = double( sum_res ), divisor = double( #request.term ) })( response.average )
        }]

        [ percentage( request )( response ) {
            div@Calculator( { dividend = request.term, divisor = 100.0 })( div_res )
            mul@Calculator( { factor[0] = div_res, factor[1] = request.percentage })( response )
        }]
    }
}


