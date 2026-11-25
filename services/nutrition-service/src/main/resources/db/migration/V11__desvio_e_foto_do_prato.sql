-- Desvio e foto do prato no registro da refeição (DEV-0088, FR-51). O desvio é texto livre do paciente; a foto fica no
-- SeaweedFS, em bucket privado, e aqui só a chave do objeto e quando foi confirmada. Dados do paciente (BR-01).

alter table meal_log add column deviation varchar(500);
alter table meal_log add column photo_key varchar(300);
alter table meal_log add column photo_uploaded_at timestamptz;

alter table meal_log add constraint ck_meal_log_deviation check (deviation is null or char_length(btrim(deviation)) >= 1);
-- A chave e a data da foto andam juntas.
alter table meal_log add constraint ck_meal_log_photo check ((photo_key is null) = (photo_uploaded_at is null));
