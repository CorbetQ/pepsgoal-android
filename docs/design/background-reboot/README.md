# Recréation des fonds — recherche, design, art et critique

Le catalogue comporte 52 fonds illustrés : 45 créations raster originales et sept
compositions natives. Le fond uni et la photo personnelle gardent leur fonction.
La refonte est locale, sur `feature/immersive-backgrounds`, sans changement de
version ni de droits commerciaux.

- [Références](references.md) et [manifeste](references.json) : 67images internet
  pour 52 fonds, plus veille de design ; provenance, limites et rejets documentés.
- [Cahier des charges](brief.md), [52 fiches](briefs.json) et
  [décisions du designer](designer-decisions.md).
- [Journal de l'artiste](art-log.md), [direction des sept natifs](native-art-direction.md)
  et [corrections natives](native-corrections-r2.md).
- [Critique indépendante](review.md) et [révisions acceptées](accepted.json).

Les images de recherche et les candidats restent dans
`/workspace/.pepsgoal/background-reboot/`. Seules les illustrations acceptées sont
copiées dans `app/src/main/assets/backgrounds/<clé>/art/`, avec empreinte et
provenance. Les sources des formes natives, propriétés et animations restent dans
le dossier du fond. Les photographies tierces ne sont pas distribuées.

Les corrections sont jugées sur les masters puis sur les images Android de jour,
de nuit et en portrait 9:19,5. La galerie est produite par `SceneRenderTest` dans
`app/build/reports/scenes/index.html` ; les candidats possèdent leurs planches
séparées. Une compilation ne constitue pas un visa esthétique. Aucun gain de
batterie ou de FPS n'est affirmé sans mesure sur appareil.
