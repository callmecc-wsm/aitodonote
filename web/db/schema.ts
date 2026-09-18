import { integer, sqliteTable, text } from 'drizzle-orm/sqlite-core';
export const notebooks=sqliteTable('notebooks',{
 owner:text('owner').primaryKey(), data:text('data').notNull(), version:integer('version').notNull().default(0),
 lease:text('lease').notNull().default(''), leaseUntil:integer('lease_until').notNull().default(0)
});
