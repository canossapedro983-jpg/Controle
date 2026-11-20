# Controle de gastos — código-fonte

App pessoal de gastos (pt-BR, celular primeiro). Um único HTML com CSS e JS embutidos.

## Estrutura
- `app/index.html` — fonte principal (versão artefato claude.ai, usa `claude.use("db"/"user")`).
- `site/build.py` — gera `site/index.html` (versão com Supabase, login e chamada ao Apps Script do Gmail) a partir do `app/index.html`. Rode `python3 site/build.py` (ajuste os caminhos no topo se necessário).
- `site/gmail-pix.exemplo.gs` — Apps Script que lê Pix do Gmail (token removido).
- Branch `apk`: projeto Android (WebView + NotifService que lê notificações). Workflow `.github/workflows/apk.yml` gera o APK.
- Branch `gh-pages`: site publicado em https://canossapedro983-jpg.github.io/Controle/

## Fluxo de publicação
1. Editar `app/index.html` → `build.py` → `site/index.html`.
2. Copiar para `gh-pages` (index.html + manifest) e para `apk` em `app/src/main/assets/index.html`.
3. Push da `apk` dispara o build do APK (release `app`).

## Supabase
Tabelas: `docs`, `lancamentos`, `ingest_tokens`, RPC `inserir_pendente`, RLS por usuário.

## Estado / últimas mudanças
- Leitor de notificações mais rápido (push do serviço → página, rebind ao abrir, pedido de isenção de bateria; APK 5.6).
- Campo opcional "Descrição (o que comprou)" quando o pagamento é cartão (`obs` no lançamento).
- Ideias pendentes (arena): aprovar sozinho + confirmar todos, anti-duplicado, contas fixas / "quanto posso gastar hoje", fechamento do mês.
