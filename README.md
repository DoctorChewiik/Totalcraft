# Totalcraft

Mod Minecraft **NeoForge 1.21.1** de batailles tactiques inspirées de *Total War: Warhammer III*.
Recrutez des escouades de mobs, déployez-les sur un champ de bataille vu du dessus et dirigez-les à la souris,
seul ou en coop contre l'IA.

## Jouer

1. Appuyez sur **K** (ou `/totalcraft armee`) pour ouvrir le camp : chaque joueur commence avec **1500 pièces d'or**.
2. Recrutez des escouades (ex. « Horde de zombies » = 6 zombies) et des champions.
3. Choisissez une carte et cliquez sur **Lancer la bataille** (ou `/totalcraft bataille [carte]`).
4. **Déploiement** : sélectionnez vos escouades et faites un clic droit dans votre zone pour les placer, puis **PRÊT**.
5. **Bataille** : vous voyez le terrain d'en haut et donnez vos ordres.
6. Après la bataille, vous touchez de l'or (plus en cas de victoire). Les pertes sont conservées : soignez,
   améliorez ou recrutez de nouvelles troupes avant la prochaine bataille.

**Coop** : un ami fait `/totalcraft rejoindre <joueur>` (ou clique sur « Rejoindre » dans le camp) pendant le
déploiement. L'ennemi reçoit alors des renforts. Jusqu'à 3 joueurs par bataille.

### Commandes de la caméra

| Action | Contrôle |
|---|---|
| Déplacer la caméra | touches de déplacement (ZQSD / WASD), flèches, ou souris au bord de l'écran |
| Accélérer | Maj |
| Zoom | molette |
| Rotation / inclinaison | clic molette + glisser |
| Sélectionner | clic gauche, glisser pour une sélection en rectangle, Maj pour ajouter |
| Tout sélectionner | Espace |
| Se déplacer / attaquer | clic droit sur le terrain / sur une escouade ennemie |
| Halte | H |
| Sorts du champion | 1, 2, 3 ou les boutons au-dessus des cartes d'unités |
| Chat | T |

### Commandes

- `/totalcraft armee` : ouvrir le camp
- `/totalcraft bataille [carte|aleatoire]` : lancer une bataille contre l'IA
- `/totalcraft rejoindre <joueur>` : rejoindre la bataille d'un ami
- `/totalcraft abandonner` / `/totalcraft quitter`
- `/totalcraft or <montant> [joueur]` (op) : donner de l'or pour tester

## Systèmes

- **Escouades** : chaque unité est un groupe de mobs vanilla avec ses propres PV, dégâts, armure, vitesse,
  portée et moral. Elles combattent en formation.
- **Bonus/malus entre unités** : ex. hallebardiers +60% contre la cavalerie, loups +60% contre les archers,
  archers -40% contre les grandes créatures.
- **Moral** : une escouade qui subit trop de pertes part en déroute, puis peut se rallier. Une armée entièrement
  détruite ou en fuite perd la bataille. La mort d'un champion démoralise ses alliés.
- **Champions** : unités uniques avec 3 sorts (cri de guerre, charge, mur de boucliers, nova de feu,
  crocs, foudre, aura de soin).
- **Cartes** : six terrains de même taille (plaines, forêt, désert, toundra, terres maudites, marais) avec
  leurs bonus et malus (vitesse, dégâts, défense, PV, butin).
- **IA** : l'armée ennemie est générée selon votre nombre de victoires et cherche à contrer vos troupes.

## Ajouter des unités ou des cartes

Tout est défini en JSON, rechargeable avec `/reload` ou via un datapack :

- `src/main/resources/data/totalcraft/totalcraft/units/*.json`
- `src/main/resources/data/totalcraft/totalcraft/maps/*.json`

Champs d'une unité : `name`, `description`, `entity` (n'importe quel mob vanilla à IA par objectifs),
`squad_size`, `cost`, `category` (`infantry`, `ranged`, `cavalry`, `monster`, `champion`), `tags`, `health`,
`damage`, `armor`, `speed`, `range`, `attack_cooldown`, `projectile` (`arrow`, `frost_arrow`, `vanilla`),
`morale`, `scale`, `bonus_vs` (catégorie ou tag → multiplicateur), `abilities`, `equipment`.

## Compiler

```
./gradlew build        # le .jar est dans build/libs
./gradlew runClient    # lance le jeu avec le mod
```

Java 21 requis. La difficulté du monde doit être au moins **Facile** (en Paisible, les monstres disparaissent).
