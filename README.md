# MerchantTalk

**Des PNJ marchands qui parlent, et qui sont la seule porte d'entrée vers le shop [EconomyShopGUI](https://www.spigotmc.org/resources/economyshopgui.69927/).**

Plugin Paper 26.2 · FancyNpcs · EconomyShopGUI (gratuit ou Premium)

## Fonctionnalités

- **PNJ marchands** (via FancyNpcs) : nom, skin (envoi d'un PNG), section du shop à ouvrir, regard qui suit les joueurs.
- **Shop accessible uniquement par les PNJ** : `/shop`, `/market`, `/sellall`, `/sellgui`… sont bloqués pour les joueurs et cachés de l'auto-complétion. Toute transaction faite hors d'un marchand est annulée.
- **Répliques** tirées au hasard pour chaque moment :
  - ouverture du shop / fermeture du shop
  - achat réussi / vente réussie
  - pas assez d'argent / transaction impossible (inventaire plein…)
  - un joueur s'approche (avec délai réglable)
  - joueur sans la permission requise
- **Répliques selon l'heure et la météo du jeu** : chaque réplique peut être réservée au matin, au jour, au soir ou à la nuit, et/ou au beau temps, à la pluie ou à l'orage. La nuit, le marchand dit en priorité ses répliques « Nuit ». Les plages horaires se règlent dans `config.yml`.
- **Menu de vente** : un marchand peut ouvrir le `/sellgui` d'EconomyShopGUI au lieu d'une section. Le joueur y dépose tous ses objets d'un coup.
- **Conversation IA (optionnelle)** : branche une clé **Groq**, **Google Gemini** ou n'importe quelle API compatible OpenAI. Près du marchand, le joueur l'appelle dans le chat (« @Bjorn salut ! ») et il répond avec la personnalité écrite sur le site. Il se souvient de la discussion, et on peut tester la conversation directement depuis le site.
  - L'IA connaît **les vrais objets et prix** de sa boutique (promo comprise) : « Combien le diamant ? » → la bonne réponse.
- **Clients fidèles** : chaque marchand se souvient des joueurs (visites, achats, ventes). Les habitués ont leurs propres répliques, les phrases peuvent utiliser `{visits}`, `{spent}`, `{earned}`, et le site affiche les meilleurs clients.
- **Promotions (optionnel)** : réduction sur les achats et/ou bonus sur les ventes, tous les jours ou certains jours. Le marchand l'annonce aux joueurs qui passent.
- **Horaires d'ouverture (optionnel)** : en dehors de ses heures, le marchand dort, disparaît ou refuse de vendre.
- **Animations (optionnel)** : il s'incline pour saluer, prend l'objet acheté en main, des pièces volent.
- **Marchand ambulant (optionnel)** : il voyage entre plusieurs emplacements et l'annonce à tout le serveur.
- **Marchands réservés** : une permission par marchand (ex. `rang.vip`). Les autres joueurs se font gentiment refuser.
- **Notes de musique** au-dessus du PNJ tant qu'il parle (désactivable).
- **Statistiques** par marchand : ouvertures, achats, ventes, argent dépensé et gagné, refus faute d'argent.
- **Voix spatialisées** : chaque réplique peut avoir un fichier audio. Le son sort du PNJ et baisse avec la distance pour les joueurs autour (catégorie « Voix » des options du jeu).
- **Resource pack généré automatiquement** et hébergé par le plugin. Il est renvoyé aux joueurs dès qu'une voix change.
- **Interface web** intégrée, protégée par mot de passe :
  - gestion des marchands, skins (aperçu du personnage en pied) et répliques ;
  - **aperçu en direct** des couleurs MiniMessage, dégradés compris, tel que le message apparaîtra dans le chat ;
  - bouton **« Tester en jeu »** : le marchand dit une réplique au joueur connecté le plus proche ;
  - duplication d'un marchand, recherche, Ctrl+S pour enregistrer ;
  - envoi de voix MP3 / WAV / OGG / M4A, ou enregistrement au micro : le navigateur les **convertit en OGG mono** (obligatoire pour la spatialisation), **coupe les silences** et **égalise le volume** ;
  - écoute des voix et forme d'onde directement sur le site.

## Installation

1. Installe sur le serveur :
   - [FancyNpcs](https://modrinth.com/plugin/fancynpcs) ;
   - [EconomyShopGUI](https://www.spigotmc.org/resources/economyshopgui.69927/), avec Vault et un plugin d'économie ;
   - `MerchantTalk.jar`.
2. Démarre le serveur une fois. Le mot de passe du site s'affiche dans la console. Il est aussi dans `plugins/MerchantTalk/config.yml`.
3. Ajoute un **port supplémentaire** à ton serveur (sur Minestrator : panel → ports / allocations).
4. Dans `plugins/MerchantTalk/config.yml` :
   ```yaml
   web:
     port: 25570            # le port ajouté à l'étape 3
     public-url: "http://IP-DU-SERVEUR:25570"
   ```
   `public-url` doit être accessible **par les joueurs** : c'est l'adresse de téléchargement du resource pack.
5. Tape `/mt reload`, puis ouvre `public-url` dans ton navigateur.

## Utilisation

1. En jeu, place-toi où tu veux le marchand, puis tape `/mt create forgeron`.
2. Sur le site, choisis le marchand :
   - règle son nom et la section du shop (ex. `Blocks`), puis envoie son skin ;
   - dans l'onglet **Voix**, ajoute tes fichiers audio ;
   - reviens au marchand, ajoute des répliques (texte et/ou voix), puis clique sur **Enregistrer**.
3. Clic droit sur le PNJ : le shop s'ouvre et le marchand parle.

Dans les textes, `{player}` est remplacé par le pseudo du joueur. Les couleurs [MiniMessage](https://docs.advntr.dev/minimessage/format.html) sont acceptées (`<gold>`, `<bold>`, `<#ff8800>`…).

## Commandes (`/merchanttalk` ou `/mt`, permission `merchanttalk.admin`)

| Commande | Effet |
|---|---|
| `/mt create <id>` | Crée un marchand à ta position |
| `/mt place <id>` | Déplace un marchand à ta position |
| `/mt tp <id>` | Téléporte au marchand |
| `/mt delete <id>` | Supprime un marchand |
| `/mt open <id> [joueur]` | Ouvre le shop d'un marchand (test) |
| `/mt list` | Liste des marchands |
| `/mt web` | Adresse du site |
| `/mt sendpack [joueur]` | Renvoie le resource pack |
| `/mt stats` | Statistiques des marchands |
| `/mt blocked` | Commandes du shop bloquées |
| `/mt addspot <id> [nom]` | Ajoute ta position à la tournée d'un marchand ambulant |
| `/mt spots <id>` | Liste les emplacements de la tournée |
| `/mt delspot <id> <numéro>` | Retire un emplacement |
| `/mt nextspot <id>` | Envoie le marchand à l'emplacement suivant |
| `/mt reload` | Recharge config et données |

La permission `merchanttalk.bypass` (OP par défaut) permet d'utiliser `/shop` directement et dispense du resource pack obligatoire.

## Configuration

Tout est commenté dans `config.yml` :
- port du site ;
- pack obligatoire ou non ;
- message d'expulsion ;
- format du chat ;
- distance d'écoute des voix ;
- commandes utilisées pour ouvrir le shop ;
- commandes bloquées ;
- IA : activation, fournisseur, clé API, modèle, distance, langue, limites de requêtes.

### Activer la conversation IA

1. Crée une clé gratuite : [Groq](https://console.groq.com/keys) (rapide) ou [Google AI Studio](https://aistudio.google.com/apikey).
2. Dans `config.yml` : `ai.enabled: true`, `ai.provider: groq` (ou `gemini`), `ai.api-key: "ta-clé"`, puis `/mt reload`.
3. Sur le site, carte **« Conversation IA »** du marchand : coche la case, écris sa personnalité et teste-la.

Les messages des joueurs qui parlent aux marchands sont envoyés au fournisseur d'IA choisi.

## Compiler

```bash
./gradlew build
```

Le jar se trouve dans `build/libs/`. Il faut Java 25 ou plus récent.

## Crédits

- Conversion OGG dans le navigateur : [wasm-media-encoders](https://github.com/arseneyr/wasm-media-encoders) (licence MIT, voir `src/main/resources/web/vendor/`).
- PNJ : [FancyNpcs](https://github.com/FancyInnovations/FancyPlugins). Shop : [EconomyShopGUI](https://github.com/Gypopo/EconomyShopGUI-API).

## Licence

Copyright (c) 2026 Sc4ros. **Tous droits réservés.** Toute copie, modification ou redistribution
est interdite sans l'accord écrit de l'auteur. Voir [LICENSE](LICENSE).
