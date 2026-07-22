# IBM MQ Configuration


## Queue Manager

QM1


## Queue utilisée

PAYMENT.IN


## Connexion locale

Host: localhost

Port: 1414

Channel: DEV.APP.SVRCONN


## Flux

Application Back Office

        |

        v

PAYMENT.IN Queue

        |

        v

Spring JMS Listener
