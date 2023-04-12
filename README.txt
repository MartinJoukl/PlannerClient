INSTRUKCE K POUŽITÍ
	Aplikace jsou dostupné na Githubu: https://github.com/MartinJoukl/PlannerClient.git (plánovač) a https://github.com/MartinJoukl/PlannerServer.git (server).
------
Úvodní konfigurace - server
	1) Do kořenového adresáře je nutné umístit konfigurační soubor config.json. Tento konfigurační soubor má strukturu uvedenou v následujícím příkladě:
{
    "queues":[
        {
            "name":"test Priority Queue",
            "agents":["WINDOWS","LINUX"],
            "planningMode":"PRIORITY_QUEUE",
            "capacity": 100,
            "priority": 5
        },
        {
            "name":"test FIFO",
            "agents":["WINDOWS","LINUX"],
            "planningMode":"FIFO",
            "capacity": 100,
            "priority": 5
        }
    ],
    "port":6660,
    "clientTimeoutDeadline": 20000,
    "clientNoResponseTime":2000,
    "taskTimeoutDelay":2000
}
	Kde "queues" je pole front, obsahující objekty reprezentující fronty - jejich vlastnosti "name" - jméno, "agents" - podporované operační systémy, "planningMode" - plánovací mód (prioritní fronta či FIFO), 
	"capacity" je kapacita fronty a "priority" je priorita fronty.

	2) Dále spustíme aplikaci a vygenerujeme privátní a veřejný klíč v záložce configuration managemens (tlačítko Generate new), které se uloží do storage/keys
 	(storage/keys/serverPrivate.key a C:\Users\joukl\IdeaProjects\PlannerServer\storage\keys\serverPublic.key). Veřejný klíč bude nutno později vložit do aplikace klienta.
	3) Pro správnou komunikaci je nutné načíst souboru veřejný klíč klienta pomocí tlačítka Load private key. Tento klíč lze vygenerovat na klientu (viz. krok 4 konfigurace klienta).
	4) Pokud nemáme žádné fronty (získáné z konfigurace), tak přidáme novou frontu pomocí "Add queue". 
	5) Úkol zadáme pomocí tlačítka upload task a zvolený soubor vybereme - struktura úkolu viz. přiložený příkladový úkol - example/exampleFIFO a example/examplePriorityQueue. Po vybrání složky totoho úkolu dojde 
	k jeho přidání do fronty
	6) Spustíme naslouchání - na panelu dashboard klikneme na "Start listening/stop listening" - pro vykonání úlohy potřebujeme nakonfigurovat minimálně jednoho aktivního klienta - viz. konfigurace klienta
	

------
Úvodní konfigurace - klient
	1) Do kořenového adresáře je nutné umístit konfigurační soubor config.json. Tento konfigurační soubor má strukturu uvedenou v následujícím příkladě:
{
    "port": 6660,
    "host": "127.0.0.1",
    "agent": "WINDOWS",
    "subscribedQueues":["test Priority Queue","test FIFO"],
    "availableResources": 100
}
	Kde port je port na serveru, host je IP adresa či doménové jméno serveru, agent je operační systém klienta, subscribed queues jsou odebírané fronty a availableResources jsou dostupné virtuální prostředky.
	
	2) Dále je nutné pro komunikaci se serverem vložit do složky storage/keys/serverPublic.key veřejný klíč klienta - ten lze vygenerovat v aplikaci serveru a poté exportovat (pomocí export to file na serveru).
	3) Po provedení těchto kroků je možno aplikaci spustit například pomocí vývojového prostředí (např. IntelliJ IDEA) či spustit jako standardní Java aplikaci.
	4) Dále přidáme veřejný a privátní klíč klienta - buďto je umístíme do storage/keys/serverPublic.key a storage/keys/clientPublic.key a poté spustíme příkaz "rk" - a nebo je můžeme vygenerovat pomocí příkazu gc.
	5) Úvodní konfiguraci klienta máme hotovou, polling spustíme pomocí příkazu "cn".


Společné pro obě aplikace:
	Aplikaci lze spustit pomocí libovolného vývojového prostředí (například pomocí IntelliJ IDEA) a nebo ji lze zkompilovat kompilátorem Java a spustit.