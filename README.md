# DocVoice

**Português** · [English](README.en.md)

Uma aplicação Android que **lê documentos PDF em voz alta** e guarda o sítio
onde parou.

Sem conta. Sem publicidade. Sem Internet: a aplicação **não tem sequer
autorização para aceder à rede**, por isso nenhum documento sai do telemóvel.
Isto está no próprio ficheiro de configuração da aplicação e pode ser
verificado por qualquer pessoa.

Livre e gratuita, e assim continuará — ver [Licença](#licença).

### ⬇ Descarregar

**[DocVoice.apk](../../releases/latest/download/DocVoice.apk)** — abra este
ficheiro no telemóvel. A ligação aponta sempre para a versão mais recente.

Como instalar está [aqui em baixo](#instalar-no-telemóvel), em cinco passos.
Precisa de Android 8.0 ou mais recente.

### 🔎 Veio ajudar a testar?

Leia **[COMO_TESTAR.md](COMO_TESTAR.md)**: onze perguntas simples, sem termos
técnicos, e um sítio para escrever o que correu mal. É a forma mais útil de
ajudar este projecto.

---

## Para quem é

Para quem tem de atravessar documentos longos e prefere ouvi-los: contratos,
sentenças, manuais, relatórios. Para quem lê com dificuldade textos compridos.
Para quem quer ouvir um processo no caminho para o trabalho, com o telemóvel
no bolso e o ecrã apagado.

## O que faz

- Abre um PDF que tenha **texto** lá dentro e mostra-o em letra grande sobre
  fundo escuro.
- Lê em voz alta, parágrafo a parágrafo, com a voz do próprio telemóvel.
  Prefere português de Portugal; se não houver, usa o que houver e avisa.
- Marca o parágrafo que está a ser lido e acompanha o texto sozinho.
- **Guarda onde ficou** — a cada parágrafo, não só quando se carrega em parar.
  Fecha-se a aplicação, volta-se no dia seguinte, continua no mesmo sítio.
- Continua a ler com o **ecrã apagado** e a aplicação fechada, com uma barra de
  notificação que traz recuar, parar e avançar.
- Cala-se quando entra uma chamada.
- Velocidade de 0,75× a 2×.
- Reconhece se o documento está em português, russo ou inglês, e escolhe a voz.
- Antes de cada página nova mostra uma marca discreta: `fl. 1`, `fl. 2`.

## O que ainda **não** faz

Esta aplicação está inacabada, e é honesto dizê-lo:

- **Documentos digitalizados não são lidos.** Se o PDF for uma fotografia de
  páginas, avisa que não tem texto e fica por aí. O reconhecimento de imagem
  (OCR) ainda não está feito.
- **O botão dos auscultadores não funciona**, nem os comandos no ecrã de
  bloqueio.
- Nunca foi usada em muitos telemóveis diferentes. Alguns fabricantes desligam
  aplicações em segundo plano à sua maneira, e isso ainda não está estudado.

## Instalar no telemóvel

1. Toque em **[DocVoice.apk](../../releases/latest/download/DocVoice.apk)** para
   descarregar. (Todas as versões estão em [Releases](../../releases).)
2. Abra o ficheiro no telemóvel — aplicação **Ficheiros** ou **Transferências**.
3. O telemóvel vai avisar que a aplicação não vem da loja Play. É esperado:
   toque em **Definições**, ligue **Permitir desta origem**, volte atrás e
   toque em **Instalar**.
4. Se aparecer um aviso do Play Protect, escolha **Instalar mesmo assim**.
5. Da primeira vez que carregar no botão de leitura, o telemóvel pergunta se a
   aplicação pode enviar notificações. Responda **Permitir** — é a barra com os
   botões de comando.

Mais nenhuma autorização é pedida. Se o telemóvel pedir outra coisa qualquer,
isso é um problema: [abra um issue](../../issues).

**Precisa de:** Android 8.0 ou mais recente.

Esta é uma versão de ensaio, assinada com a chave de depuração. Ao actualizar
a partir de uma versão anterior pode ser preciso desinstalar primeiro.

## Autorizações que a aplicação pede, e porquê

| Autorização | Para quê |
|---|---|
| Serviço em primeiro plano | continuar a ler depois de sair da aplicação |
| Manter o processador acordado | a voz não se partir com o ecrã apagado |
| Notificações | a barra com recuar, parar e avançar |

Não pede acesso à Internet, aos contactos, à localização, nem ao cartão de
memória. O documento é escolhido pelo sistema, e só esse é lido.

## Construir a partir do código

Precisa de um computador com Java 17 e o SDK do Android instalados.

```sh
git clone <endereço deste repositório>
cd DocVoice
./gradlew testDebugUnitTest    # os testes, sem telemóvel nenhum
./gradlew assembleDebug        # o ficheiro .apk
```

O ficheiro fica em `app/build/outputs/apk/debug/`.

Cada alteração enviada para este repositório é construída e testada
automaticamente; cada etiqueta de versão publica um `.apk` em
[Releases](../../releases).

## Como está feita

Kotlin e Jetpack Compose. O texto é extraído com
[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android); a voz é a do
próprio sistema Android. A posição e a lista de documentos ficam guardadas
em DataStore — três campos, sem base de dados.

O texto é cortado em blocos de 300 a 350 caracteres, e **só em pontuação**:
um bloco comprido é melhor do que uma ideia cortada a meio. Abreviaturas como
`art.º`, `fls.`, `Cf.` e números como `15.000` não fecham frase.

## Ajudar

O mais útil de tudo: **instalar, usar com um documento a sério, e dizer o que
correu mal**. Marca e modelo do telemóvel, o que aconteceu, e uma fotografia do
ecrã se houver alguma coisa estranha.

**[COMO_TESTAR.md](COMO_TESTAR.md)** diz exactamente o que experimentar: onze
perguntas, meia hora de trabalho, nenhuma delas técnica. Depois
[abra um issue](../../issues) ou responda a quem lhe pediu.

Não é preciso perceber de programação para ajudar.

## Licença

[GNU General Public License v3.0](LICENSE).

Em palavras simples: qualquer pessoa pode usar, estudar, alterar e distribuir
esta aplicação. Quem a distribuir alterada tem de continuar a entregar o
código, com a mesma licença. Ninguém pode pegar nisto, fechá-lo e vendê-lo
como se fosse seu.

É de propósito: esta aplicação foi feita para ficar livre e gratuita.
