# Tirs de charge

Deux injecteurs, un par porte d'entrée du système :

| Fichier | Cible | Ce qu'il mesure |
|---------|-------|-----------------|
| `MqInjector.java` | file `PAYMENT.REQUEST.QUEUE` | débit d'ingestion soutenu (messages/s) |
| `k6-api.js` | API REST | p95 des listes, des pages par curseur et de `/stats` |

Les deux supposent la pile démarrée (`docker compose up -d`) et, pour l'API, un compte
valide (`admin`/`admin` en configuration de démonstration).

## Ingestion MQ

```bash
cd backend
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "$(cat target/cp.txt)" ../infra/load/MqInjector.java --count 20000 --threads 4
```

Sous Windows (PowerShell), le séparateur de classpath est `;` :

```powershell
cd backend
./mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
java -cp (Get-Content target/cp.txt) ../infra/load/MqInjector.java --count 20000 --threads 4
```

Options : `--host --port --qmgr --channel --user --password --queue --count --threads
--rate --persistent`. `--rate 0` (défaut) envoie aussi vite que possible ; une valeur
positive impose un débit cible en messages/s, ce qui permet de chercher le point de
rupture par paliers plutôt que de saturer d'emblée.

Le programme n'affiche que le débit de **production**. Le débit de **consommation** — le
seul qui compte — se lit ailleurs, pendant et après le tir :

```bash
# Messages effectivement persistés, doublons, rejets, rollbacks
curl -s localhost:8080/actuator/prometheus -H "Authorization: Bearer $TOKEN" \
  | grep -E 'payment_mq_messages|payment_mq_listener_rollbacks|payment_mq_processing_seconds'

# Profondeur de file : si elle croît, la consommation ne suit pas
docker exec payment-mq bash -c "echo 'DISPLAY QLOCAL(PAYMENT.REQUEST.QUEUE) CURDEPTH' | runmqsc QM1"

# Occupation du pool JDBC : saturé, il devient le facteur limitant
curl -s localhost:8080/actuator/metrics/hikaricp.connections.usage -H "Authorization: Bearer $TOKEN"
```

## API REST

```bash
k6 run infra/load/k6-api.js
k6 run -e BASE_URL=http://localhost:8080 -e VUS=100 -e DURATION=5m infra/load/k6-api.js
```

Variables : `BASE_URL`, `USERNAME`, `PASSWORD`, `VUS`, `DURATION`, `RAMP`.

Les seuils font **échouer** le tir (code de sortie non nul) :

| Seuil | Valeur | Pourquoi |
|-------|--------|----------|
| `payment_list_duration` p95 | < 500 ms | page tirée au hasard dans les 20 premières, donc `OFFSET` réel |
| `payment_cursor_duration` p95 | < 300 ms | le keyset doit rester insensible à la profondeur |
| `payment_stats_duration` p95 | < 150 ms | au-delà, l'agrégat n'est plus servi depuis le cache |
| `http_req_failed` | < 1 % | — |

Un tir sur une table vide ne prouve rien : injecter d'abord quelques centaines de milliers
de messages avec `MqInjector`, sinon PostgreSQL répond depuis son cache et tous les seuils
passent quelle que soit la qualité des requêtes.

## Résultats

`infra/load/results/` est ignoré par git : y déposer les sorties (`k6 run --out
json=results/…`) sans polluer l'historique.
