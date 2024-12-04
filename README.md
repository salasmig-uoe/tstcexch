# CardExchange Integration

This is the WSO2 configuration to handle webhook callbacks from CardExchange.

When a card is issued or blocked in CardExchange, it triggers a POST request to a webhook with a JSON payload 
containing the person's details. From this, the integration extracts the current active card details (if available), 
and send them to IDM and CardAPI. CardAPI updates the person's photo in the Datastore and maintains the legacy CardDB 
table until it's no longer needed by downstream systems. IDM keeps the authoritative record of the person's current 
card numbers, including the Card Number, Mifare Chip Serial Number, and Library Number.

## Data flow
This flowchart summarises just the main data flow in this integration. 

```mermaid
flowchart TB
  Card((Card API app))
  IDM((IDM Write MS))
  CX((CardExchange))-->|Person data|AWH
  AWH --> M1
  AIW --> IDM
  ACA --> Card

  subgraph APIM[Choreo]
    direction TB
    AWH(Webhook receiver)
    ACA(CardAPI endpoint)
    AIW(IDMWrite endpoint)
    T(Choreo Token Endpoint)
  end

  subgraph WSO2[WSO2 Enterprise Integrator]
    direction TB
    subgraph I[Rest API]
      M1[[Authentication Mediator]] -->M2
      M2[[DataMapper]]
      M2 --> M4
      M4[[Prep IDMWrite Payload]]
    end

    subgraph MP[Message Processing]
      subgraph IM[IDMWrite Messages]
        OI[[OAuth Token]] -->DI
        DI[(IDMWrite Store)]
        IP[[IDMWrite Processor]]
      end

      subgraph CM[CardAPI Messages]
        OC[[OAuth Token]] -->DC
        DC[(CardAPI Store)]
        CP[[CardAPI Processor]]
      end

      subgraph IFH[IDMWrite Failure Handling]
        FI[(IDMWrite Failures Store)]
        FPI[[IDMWrite Failures Processor]]
      end

      subgraph CFH[CardAPI Failure Handling]
        FC[(CardAPI Failures Store)]
        FPC[[CardAPI Failures Processor]]
      end
    end

    M2 -->|CardAPI Payload|OC
    M4 --> OI

    OC <--> T
    OI <--> T

    DC --> CP --> ACA
    DI --> IP --> AIW

    AIW .-> FI
    ACA .-> FC
    FC --> FPC --> OC
    FI --> FPI --> OI

end
   
```

## Dependencies

### WSO2
Following environment variables should be configured when deploying this component in Choreo.

| Variable Name           | Example                                                          |
|-------------------------|------------------------------------------------------------------|
| `CARDEXCH_USERNAME`     | `The App's Client Key in Choreo`                                 |
| `OAUTH_TOKEN_ENDPOINT`  | `https://api.asgardeo.io/t/universityofedinburgh/oauth2/token`   |
| `IDM_WRITE_ENDPOINT`    | `https://dev.api.ed.ac.uk/identity/idm/v1/persons`               |
| `CARD_API_ENDPOINT`     | `https://dev.api.ed.ac.uk/card/card/v1/cardexchange`             |
| `jmsurl`                | `tcp://localhost:61616`                                          |
| `jmsuser`               | `system`                                                         | 

Following secrets should be configured when deploying this component in Choreo.

| Secret Name         | Purpose                                     |
|---------------------|---------------------------------------------|
| `CARDEXCH_PASSWORD` | `The App's Client Secret in Choreo`         |
| `SIG_CARDEXCH_KEY`  | `The CardExchange signature verifying key`  |
| `jmspass`           | `to authenticate Amazon MQ`                 |

## Local Development

### Development Tools

- Integration Studio from WSO2. This is a customised Eclipse editor and comes bundled with Micro Integrator and can be used for most development and basic testing.
- Alternatively there is a WSO2 Enterprise Integrator Plugin for VSCode.
- UoE WSO2 Docker. Our own preconfigured docker instance with everything needed for local development: \
  <https://gitlab.is.ed.ac.uk/core-software/wso2docker>
- a Mocklab <https://app.mocklab.io/> account or similar to mock API calls during development.

## Deployment

Deployment is all managed through Choreo.

## Release notes


- 2024 November 24<sup>th</sup>:   
  migration and deployment to Choreo.
