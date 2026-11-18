# Lembretes de Trabalho

Aplicativo de desktop para Windows, feito em Java (Swing) com visual azul e branco, que ajuda a organizar o dia de trabalho:

- **Lembretes de ponto, remédios e pausas**: um aviso pequeno aparece por cima de qualquer janela, com um som curto e baixo.
- **Reuniões de CS**: anotações das reuniões, com horário e avisos automáticos 1 hora antes (para enviar a confirmação) e 5 minutos antes do início.
- **Clientes para acompanhar**: lista com prioridade, data do próximo contato e status, além de um aviso diário dos clientes pendentes.

---

## Funcionalidades

### Lembretes (ponto, remédios e pausas)
- Crie quantos lembretes quiser, cada um com tipo, horário (HH:mm), mensagem e a opção **apenas de segunda a sexta**.
- O aviso aparece no **canto inferior direito**, sempre por cima das outras janelas. Ele **não tira o foco**, então dá para continuar digitando.
- Botões do aviso: **OK** ou **Adiar 5 min**.
- Se vários avisos aparecerem ao mesmo tempo, eles ficam empilhados.
- Botões de apoio: **Pausa rápida**, **Ativar / Desativar**, **Testar popup** e **Som ativado**.
- Já vem com exemplos: os pontos de 08:00, 12:00, 13:00 e 18:00, pausas às 10:00 e às 15:30 e um lembrete de remédio desativado.

### Reuniões de CS
- Campos: data, horário, cliente, participantes, assunto, anotações e próximos passos.
- **Aviso 1 hora antes**: lembra de enviar a confirmação. O botão **Copiar mensagem** copia um texto pronto para colar no WhatsApp ou no e-mail.
- **Aviso 5 minutos antes**: avisa que a reunião vai começar.
- Se o programa for aberto já dentro da última hora antes da reunião, o aviso de confirmação aparece na hora.
- Cada aviso aparece uma única vez. Se a data ou o horário mudar, os avisos são programados de novo.
- As reuniões de hoje que ainda vão acontecer ficam destacadas.
- Tem **busca** por cliente, assunto, anotações ou participantes e **exportação para .txt**.
- Ao salvar uma reunião, dá para colocar o cliente direto na lista de acompanhamento, já com a data do próximo contato.

### Clientes para acompanhar
- Campos: cliente, contato, prioridade (Alta, Média ou Baixa), próximo contato, status e observações.
- Os clientes com contato para **hoje ou atrasado** ficam destacados.
- A lista é ordenada pela data do próximo contato e depois pela prioridade.
- Um aviso com os pendentes aparece **ao abrir o programa** e **todo dia às 09:00**.
- A opção **Ocultar resolvidos** esconde os clientes já resolvidos, e o botão **Quem acompanhar hoje?** mostra o aviso na hora.

### Funcionamento geral
- Ao fechar a janela, o programa continua rodando no **ícone perto do relógio** (bandeja do sistema).
- Só abre uma cópia do programa por vez.
- Pode abrir sozinho, minimizado, quando o Windows liga.
- **Salva automaticamente** a cada alteração.

---

## Instalação

**Não precisa instalar Java**, porque o pacote já leva o próprio Java na pasta `runtime`.

1. Baixe o `LembretesTrabalho-Windows.zip`.
2. Clique com o botão direito nele > **Extrair tudo...**. Não abra o programa de dentro do `.zip`.
   Sugestão: coloque a pasta em `C:\LembretesTrabalho`.
3. Dê dois cliques em **`LembretesTrabalho.exe`**.
4. Se aparecer *"O Windows protegeu o computador"*, clique em **Mais informações** > **Executar assim mesmo**. O aviso aparece porque o `.exe` não tem assinatura digital.

### Abrir junto com o Windows (opcional)
Rode o **`iniciar_com_windows.bat`**. Ele cria um atalho na pasta de Inicialização do Windows para o programa abrir minimizado.
Se mudar a pasta do programa de lugar, rode o arquivo de novo.

Para desfazer, aperte `Win + R`, digite `shell:startup` e apague o atalho **Lembretes de Trabalho**.

---

## Estrutura de pastas

```
LembretesTrabalho/
├── LembretesTrabalho.exe        ← abre o programa
├── iniciar_com_windows.bat      ← abrir junto com o Windows (opcional)
├── LEIA-ME.txt
├── app/
│   └── LembretesTrabalho.jar    ← o programa em si
├── runtime/                     ← Java incluído (não apagar)
└── codigo-fonte/
    ├── compilar.bat
    └── src/LembretesTrabalho.java
```

> As pastas `app` e `runtime` precisam ficar ao lado do `.exe`.

---

## Dados e backup

Tudo fica salvo em um único arquivo, **fora** da pasta do programa:

```
%USERPROFILE%\LembretesTrabalho\dados.bin
```

Para abrir a pasta, cole `%USERPROFILE%\LembretesTrabalho` na barra de endereço do Explorador de Arquivos.

- **O que é salvo**: lembretes, opção de som, reuniões e clientes.
- **O que não é salvo**: os avisos adiados ("Adiar 5 min") e o tamanho ou a posição da janela.
- **Backup**: copie o `dados.bin` para outro lugar.
- **Restaurar**: feche o programa (ícone perto do relógio > **Sair**), coloque o `dados.bin` de volta na pasta e abra o programa de novo.

Se o arquivo não puder ser lido, o programa **não o apaga**: renomeia para `dados_corrompido_<data>.bin` e abre vazio.

---

## Atualizar para uma versão nova

1. Feche o programa: ícone perto do relógio > **Sair**.
2. Faça uma cópia do `dados.bin` por segurança.
3. Escolha um dos jeitos:
   - **Rápido**: troque o arquivo `app\LembretesTrabalho.jar` pelo novo.
   - **Completo**: extraia o `.zip` novo por cima da pasta antiga.
4. Abra o programa. Os dados continuam lá.

---

## Para desenvolvedores

### Tecnologias
- Java com Swing e AWT, num único arquivo-fonte e **sem dependências externas**.
- Compilado com `--release 8`, então o `.jar` roda do Java 8 em diante.
- Som gerado na hora com `javax.sound.sampled` (dois tons curtos com volume em 12%).
- Dados gravados com serialização Java (`ObjectOutputStream`), com `serialVersionUID` fixo.
- O `.exe` é um lançador em C, compilado com Zig/MinGW, que chama `runtime\bin\javaw.exe -jar app\LembretesTrabalho.jar`.

### Compilar o .jar
Com um JDK 8 ou mais novo instalado:

```bat
cd codigo-fonte
compilar.bat
```

Ou manualmente:

```bat
javac --release 8 -encoding UTF-8 -d build src\LembretesTrabalho.java
echo Main-Class: LembretesTrabalho> manifest.txt
jar cfm LembretesTrabalho.jar manifest.txt -C build .
```

Depois copie o `LembretesTrabalho.jar` gerado para a pasta `app\`.

### Rodar sem o .exe
```bat
javaw -jar app\LembretesTrabalho.jar
javaw -jar app\LembretesTrabalho.jar --minimizado
```

### Personalização rápida (em `LembretesTrabalho.java`)
| O quê | Onde |
|---|---|
| Cores do tema | constantes `AZUL`, `AZUL_ESCURO`, `AZUL_CLARO`, `BRANCO` |
| Volume do som | fator `0.12` no método `tom()` |
| Tempo de "Adiar" | `plusMinutes(5)` no método `adiar()` |
| Antecedência dos avisos de reunião | `minusMinutes(60)` e `minusMinutes(5)` em `verificarReunioes()` |
| Texto da confirmação | método `textoConfirmacao()` |
| Horário do aviso de clientes | `"09:00"` em `verificar()` |
| Lembretes iniciais | método `carregar()` |

### Compatibilidade dos dados
Adicionar campos novos às classes `Lembrete`, `Reuniao`, `Cliente` ou `Dados` é compatível com os dados antigos: os campos novos chegam como `null`/`false` e devem ser tratados em `carregar()`.
**Não** troque o tipo de um campo nem o nome das classes, porque isso impede a leitura do `dados.bin` antigo.

---

## Solução de problemas

| Problema | Solução |
|---|---|
| Abre uma janela preta e fecha | Use o `LembretesTrabalho.exe`, que já leva o Java junto. O `.jar` sozinho precisa de Java 8 ou mais novo instalado. |
| "Não foi possível iniciar..." | Extraia o `.zip` inteiro e confira se as pastas `app` e `runtime` estão ao lado do `.exe`. |
| "Já está aberto" | O programa está no ícone perto do relógio (às vezes escondido na setinha **^**). Clique duas vezes no ícone. |
| Os avisos não tocam som | Confira se **Som ativado** está marcado na aba Lembretes e o volume do Windows. |
| Um lembrete não apareceu | Confira se ele está **Ativo**, se o horário está certo e se a opção "seg a sex" não bloqueou no fim de semana. O programa precisa estar aberto, mesmo que só no ícone perto do relógio. |
| O Windows bloqueia o `.exe` | Clique em **Mais informações** > **Executar assim mesmo**. |
