# nablarch-mail-sender

| master | develop |
|:-----------|:------------|
|[![Build Status](https://travis-ci.org/nablarch/nablarch-mail-sender.svg?branch=master)](https://travis-ci.org/nablarch/nablarch-mail-sender)|[![Build Status](https://travis-ci.org/nablarch/nablarch-mail-sender.svg?branch=develop)](https://travis-ci.org/nablarch/nablarch-mail-sender)|


## テスト環境のセットアップ

本モジュールのテストの実行には、メールサーバーとして Apache が提供する James をセットアップし、
テストで使うメールアカウントのユーザーを追加する必要があります。

1. James のダウンロード。<br>
   アーカイブをダウンロードし、任意のパスに展開します。以下、展開したパスを $JAMES_HOME と記述します。
2. James のSMTPのポート番号の設定。<br>
   $JAMES_HOME\apps\james\SAR-INF の config.xmlを開き、「smtpserver」で検索してportの値を10025に、「pop3server」
   で検索してportの値を10110にします。
3. James の起動。<br>
   $JAMES_HOME\bin\run.bat を実行し、James を起動します。<br>
4. James の管理コンソールの起動。<br>
   telnet localhost 4555 コマンド ※ を実行して James の管理コンソールを開きます。<br>
   James のデフォルトの管理ユーザーとパスワードはroot、rootになっています。<br>

※ Windowsでは、telnetはデフォルトでは有効になっていないため、事前に、コントロールパネルの<br>
　 「Windows の機能の有効化または無効化」で、telnetクライアントを有効にする必要があります。

ユーザーの追加は、管理コンソール上で、以下のコマンドを実行して行います。
```
adduser <ユーザー名> <パスワード>
```

テストの実行には、以下のユーザーを追加する必要があります。

|ユーザー名 |パスワード |
|:----------|:----------|
|bcc1       |default    |
|bcc2       |default    |
|bcc3       |default    |
|cc1        |default    |
|cc2        |default    |
|cc3        |default    |
|from       |default    |
|reply      |default    |
|return     |default    |
|to1        |default    |
|to2        |default    |
|to3        |default    |

## テストの実行

テストの実行には、James が起動されている必要があります。<br>
$JAMES_HOME\bin\run.bat を実行し、James を起動します。
