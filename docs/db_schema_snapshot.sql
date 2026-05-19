-- Snapshot schematu Room DB (MedStockDatabase v8)

CREATE TABLE IF NOT EXISTS `medicines` (
  `id` INTEGER NOT NULL,
  `name` TEXT NOT NULL,
  `strength` TEXT,
  `activeIngredient` TEXT,
  `form` TEXT,
  `packageSize` REAL NOT NULL,
  `currentAmount` REAL NOT NULL,
  `unit` TEXT NOT NULL,
  `dailyUsage` REAL,
  `doseMorning` REAL NOT NULL,
  `doseNoon` REAL NOT NULL,
  `doseEvening` REAL NOT NULL,
  `lastStockUpdateEpochMillis` INTEGER NOT NULL,
  `expirationDate` TEXT,
  `lowStockDaysWarning` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `medicine_barcodes` (
  `medicineId` INTEGER NOT NULL,
  `rawBarcode` TEXT NOT NULL,
  `normalizedBarcode` TEXT NOT NULL,
  PRIMARY KEY(`medicineId`, `normalizedBarcode`),
  FOREIGN KEY(`medicineId`) REFERENCES `medicines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_medicine_barcodes_normalizedBarcode`
ON `medicine_barcodes` (`normalizedBarcode`);

CREATE TABLE IF NOT EXISTS `external_catalog` (
  `barcode` TEXT NOT NULL,
  `name` TEXT NOT NULL,
  `activeIngredient` TEXT,
  `strength` TEXT,
  `form` TEXT,
  `packageSize` REAL,
  `unit` TEXT,
  `source` TEXT NOT NULL,
  `leafletUrl` TEXT,
  PRIMARY KEY(`barcode`)
);
