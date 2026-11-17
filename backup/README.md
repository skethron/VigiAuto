# Backup e recuperação — 22/10/2026

## O que aconteceu

Durante a tentativa de rodar o benchmark instrumentado, o
`connectedDebugAndroidTest` (UTP) desinstalou o VigiAuto na etapa de limpeza
porque a instalação do APK de teste foi recusada pelo HyperOS
(`INSTALL_FAILED_USER_RESTRICTED`). A desinstalação apagou o banco, as fotos e
as preferências (incluindo o token Sinesp).

O banco foi recuperado de um snapshot feito minutos antes. **As fotos e as
preferências não puderam ser recuperadas.**

## Arquivos

| Arquivo | Conteúdo |
|---|---|
| `vigiauto-2026-10-22-2213.db` | snapshot anterior, 41 registros; inclui 127/128, ainda sem 129/130 |
| `vigiauto-2026-10-22-2219.db` | banco original recuperado, 41 registros, 2 com localização e os caminhos das fotos; inclui 129/130 e não inclui 127/128 |
| `vigiauto-restore-2026-10-22.db` | banco restaurado no aparelho; caminhos de fotos foram limpos |
| `vigiauto-2026-10-22-pre164.db` | snapshot antes da atualização 1.6.4, com os 42 registros atuais |
| `registros-2026-10-22.csv` | exportação legível dos 41 registros |

Os registros `129` (EFR8092) e `130` (DEI3A56) mantêm a localização completa:
Estrada Nimbus, 97, Chácara Vitapolis, Itapevi/São Paulo, CEP 06693-265.

Entre os dois snapshots, os registros `127` e `128` (PUL7H24 e PUL2H24,
capturados às 20:49) desapareceram, provavelmente por exclusão durante os
testes. Eles ainda existem no snapshot das 22:13 e podem ser mesclados, se a
exclusão não tiver sido intencional. O snapshot das 22:19 é o que está
instalado.

## Estado atual do aparelho

- APK 1.6.4 (`versionCode 19`) instalado em `br.com.vigiauto`.
- Banco migrado para `vigiauto.db`: `user_version 4`. Os 41 registros originais foram
  preservados; durante a validação o app registrou mais 1 (`PVL7H74`), total atual de
  42 registros e 3 com localização.
- Câmera e localização foram concedidas manualmente na primeira abertura. O ADB deste
  HyperOS recusou `pm grant` (`GRANT_RUNTIME_PERMISSIONS`); o diálogo do sistema teve de
  ser aceito no aparelho.
- Preferências e token Sinesp foram zerados; o app está usando o padrão
  (sem token, as consultas caem na Consulta Simples).

## Próxima atualização (1.8.0 / banco v5)

O APK `apk/VigiAuto-1.8.0-debug.apk` (`versionCode 21`) foi gerado, mas **ainda não
foi instalado**. A 1.7.0 também não chegou a ser instalada; a 1.8.0 inclui as duas
etapas e mantém o banco na **v5**. Antes de instalar, copie o banco e as fotos atuais:

```powershell
adb shell am force-stop br.com.vigiauto
adb shell run-as br.com.vigiauto ls -l databases files/photos
adb exec-out run-as br.com.vigiauto cat databases/vigiauto.db > backup\vigiauto-pre180.db
# Se existirem, copie também databases/vigiauto.db-wal e databases/vigiauto.db-shm.
# Para as fotos, copie cada arquivo de files/photos (ou o diretório inteiro).
adb install -r --user 0 --no-streaming apk\VigiAuto-1.8.0-debug.apk
```

Depois de abrir o app, confirme que o banco continua com os 42 registros e que
`user_version` passou de 4 para 5. A migração foi validada em uma cópia do snapshot
`vigiauto-2026-10-22-pre164.db`; não execute UTP/`connectedDebugAndroidTest` sem
backup, pois o runner pode desinstalar o app.

## Migração do pacote

O banco restaurado foi copiado do pacote anterior para
`br.com.vigiauto/databases/vigiauto.db` antes da desinstalação. As fotos e as
preferências antigas já não existiam. O pacote anterior foi removido depois da
conferência dos 41 registros no novo app.

## Como restaurar o banco novamente (somente build debug)

```powershell
adb install -r --user 0 --no-streaming "apk\VigiAuto-1.6.4-debug.apk"
adb shell am force-stop br.com.vigiauto
adb shell run-as br.com.vigiauto mkdir -p databases
adb shell run-as br.com.vigiauto rm -f databases/vigiauto.db-wal databases/vigiauto.db-shm
adb push "backup\vigiauto-restore-2026-10-22.db" /data/local/tmp/vigiauto.db
adb shell run-as br.com.vigiauto cp /data/local/tmp/vigiauto.db databases/vigiauto.db
adb shell run-as br.com.vigiauto chmod 600 databases/vigiauto.db
adb shell am start -n br.com.vigiauto/.MainActivity
```

## Aviso importante

Não execute `gradlew connectedDebugAndroidTest` neste aparelho sem antes
copiar banco e fotos. O UTP pode desinstalar o app ao final, apagando os dados.
Para atualizar o app, use `adb install -r --user 0 --no-streaming`.

Para um backup manual seguro:

```powershell
adb shell am force-stop br.com.vigiauto
adb shell run-as br.com.vigiauto ls -l databases files/photos
# copiar databases\vigiauto.db (+ -wal e -shm, se existirem) e files/photos
```

As fotos antigas só poderiam ser recuperadas se existir uma cópia externa;
o diretório privado do app foi removido pelo desinstalador.
