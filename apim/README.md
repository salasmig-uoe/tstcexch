# API Manager Mediator
Our Django set-up cannot handle chunked requests: the application receives an empty request body if API Manager 
sends it a chunked payload.

To disable chunking in the old version of API Manager we currently use in WSO2 Cloud, load this mediator to both the
In Flow and Out Flow under the Message Mediation Policies.  

In later versions, disabling chunking can be done directly by selecting the appropriate Policy when configuring the API
and this mediator file will not be needed.
