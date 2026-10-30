# Banco de imagens dos produtos

As 48 imagens em `crm-loja-backend/src/main/resources/assets/produtos/<peça>/` (frente, traseira, lateral e
detalhes de cada uma das 12 peças do catálogo de demonstração) são **ilustrações feitas pela equipe em código**,
não fotografias. Por isso as quatro vistas mostram sempre a mesma peça, com a mesma cor, modelagem e estampa,
sem marca, logotipo ou texto.

- **Autoria:** equipe Orden. **Licença:** CC0, uso livre no projeto. No sistema, cada imagem aparece marcada como
  "Ilustração", com a autoria e a licença.
- **Por que não fotos da internet:** os bancos gratuitos com licença comercial quase nunca têm as quatro vistas da
  mesma peça, e misturar fotos de peças diferentes passaria uma informação falsa ao cliente.
- **Trocar por fotos de verdade:** na ficha do produto, o gerente usa **Enviar foto** (JPG, PNG ou WEBP, até 2 MB)
  e **Usar como capa**; as ilustrações podem ser excluídas depois.

## Gerar de novo

```bash
python gerar_svg.py                       # cria svg/<peça>/<vista>.svg
node rasterizar.mjs svg jpg               # precisa de playwright-core e do Google Chrome
```

Copie `jpg/*` para `crm-loja-backend/src/main/resources/assets/produtos/`. O `ImagensProdutosSeeder` grava as
imagens no banco na subida, só nos produtos que ainda não têm nenhuma.
