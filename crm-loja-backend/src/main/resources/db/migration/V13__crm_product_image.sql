-- V13 · Galeria de imagens dos produtos (catálogo visual).
-- A imagem fica no próprio banco (bytea): o plano grátis do Render apaga arquivos a cada deploy, e assim as fotos
-- entram no mesmo backup dos dados. Cada imagem tem até 2 MB e cada produto até 8 imagens (regra no Service).
CREATE TABLE product_image (
  id            bigint GENERATED ALWAYS AS IDENTITY,
  product_id    bigint        NOT NULL,
  view          varchar(10)   NOT NULL,
  position      smallint      NOT NULL,
  content_type  varchar(30)   NOT NULL,
  data          bytea         NOT NULL,
  size_bytes    integer       NOT NULL,
  source        varchar(200)  NOT NULL,
  license       varchar(120)  NOT NULL,
  illustration  boolean       NOT NULL DEFAULT false,
  created_by    bigint,
  created_at    timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_product_image PRIMARY KEY (id),
  CONSTRAINT ux_product_image_position UNIQUE (product_id, position) DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT ck_product_image_view CHECK (view IN ('FRONT', 'BACK', 'SIDE', 'DETAIL', 'OTHER')),
  CONSTRAINT ck_product_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
  CONSTRAINT ck_product_image_size CHECK (size_bytes > 0 AND size_bytes <= 2097152),
  -- A exclusão de produto só acontece no caso liberado pelo V5 (só estoque inicial); as fotos vão junto.
  CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
  CONSTRAINT fk_product_image_user FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL
);
