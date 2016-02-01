# $Revision$
# $Date$

INSERT INTO ofVersion (name, version) VALUES ('userservice', 2);

CREATE TABLE ofPushMessages (
   msgId            BIGINT            NOT NULL AUTO_INCREMENT,
   msgAction        VARCHAR(64)       NOT NULL,
   msgTime          TIMESTAMP         NOT NULL,
   msgExpire        TIMESTAMP         NULL,
   forUser          VARCHAR(255)      NOT NULL,
   forResource      VARCHAR(255)      NOT NULL,
   isDurable        INT               NOT NULL,
   isUnique         INT               NOT NULL,
   aux1             TEXT              NULL,
   aux2             TEXT              NULL,
   auxData          TEXT              NULL,
   PRIMARY KEY (msgId)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPushDelivery (
   dlvrID       BIGINT                NOT NULL AUTO_INCREMENT,
   msgId        BIGINT                NOT NULL,
   dlvrTime     TIMESTAMP             NOT NULL,
   forUser      VARCHAR(255)          NOT NULL,
   forResource  VARCHAR(255)          NOT NULL,
   dlvrStatus   INT                   NOT NULL,
   PRIMARY KEY(dlvrID),
   CONSTRAINT UNIQUE INDEX (msgId, forUser, forResource),
   FOREIGN KEY (msgId)
           REFERENCES ofPushMessages(msgId)
           ON DELETE CASCADE
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPushTokenApple (
	ofUser        VARCHAR(255) NOT NULL,
	ofResource    VARCHAR(255) NOT NULL,
	ofPlatform    VARCHAR(16)  NOT NULL,
	ofDeviceToken VARCHAR(255) NOT NULL,
	ofFakeUdid    VARCHAR(128) NOT NULL,
	ofVersion     VARCHAR(32)  NULL,
	ofAppVersion  VARCHAR(32)  NULL,
	ofOsVersion   VARCHAR(32)  NULL,
	ofLangs       VARCHAR(255) NULL,
	ofDebug       TINYINT      NOT NULL DEFAULT 0,
	ofLastUpdate  TIMESTAMP    NOT NULL,
	ofAuxJson     TEXT         NULL,
  PRIMARY KEY (ofUser, ofResource)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPhxLastActivity (
   ofUser         VARCHAR(255) NOT NULL,
   ofResource     VARCHAR(255) NOT NULL,
   ofActTime      TIMESTAMP    NOT NULL,
   ofLastStatus   INT          NOT NULL DEFAULT 1,
   PRIMARY KEY (ofUser, ofResource)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE ofPhxPlatformMessages (
   ofMsgId            BIGINT            NOT NULL AUTO_INCREMENT,
   ofMsgKey           VARCHAR(64)       NULL,
   ofMsgAction        VARCHAR(64)       NOT NULL,
   ofMsgTime          TIMESTAMP         NOT NULL,
   ofMsgExpire        TIMESTAMP         NULL,
   ofForUser          VARCHAR(255)      NOT NULL,
   ofForResource      VARCHAR(255)      NULL,
   ofFromUser         VARCHAR(255)      NULL,
   ofFromResource     VARCHAR(255)      NULL,
   ofMsgType          INT               NOT NULL DEFAULT 0,
   ofIsDurable        INT               NOT NULL DEFAULT 0,
   ofIsUnique         INT               NOT NULL DEFAULT 1,
   ofAckWait          INT               NOT NULL DEFAULT 1,
   ofPriority         INT               NOT NULL DEFAULT 0,
   ofAlertKey         INT               NULL,
   ofAux1             TEXT              NULL,
   ofAux2             TEXT              NULL,
   ofAuxData          TEXT              NULL,
   PRIMARY KEY (ofMsgId)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;

CREATE TABLE  `opensips_fire`.`ofPhxPushLocalization` (
   `id` BIGINT NOT NULL ,
   `ofStringKey` VARCHAR( 64 ) NOT NULL ,
   `ofLangCode` VARCHAR( 6 ) NOT NULL DEFAULT  'en',
   `ofPlural` ENUM(  'none',  'zero',  'one',  'two',  'few',  'many',  'other' ) NOT NULL DEFAULT  'none',
   `ofTranslation` TEXT NOT NULL ,
   PRIMARY KEY (`id`) ,
   INDEX (`ofStringKey`)
) ENGINE=INNODB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8;