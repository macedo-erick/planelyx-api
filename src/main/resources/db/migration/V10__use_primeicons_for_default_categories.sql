-- Switches default category icons from Font Awesome to PrimeIcons (pi-* class names).
UPDATE category SET icon = 'pi-shopping-cart' WHERE owner_id IS NULL AND name = 'Groceries';
UPDATE category SET icon = 'pi-home' WHERE owner_id IS NULL AND name = 'Housing';
UPDATE category SET icon = 'pi-bolt' WHERE owner_id IS NULL AND name = 'Utilities';
UPDATE category SET icon = 'pi-car' WHERE owner_id IS NULL AND name = 'Transportation';
UPDATE category SET icon = 'pi-shop' WHERE owner_id IS NULL AND name = 'Dining Out';
UPDATE category SET icon = 'pi-heart-fill' WHERE owner_id IS NULL AND name = 'Health';
UPDATE category SET icon = 'pi-video' WHERE owner_id IS NULL AND name = 'Entertainment';
UPDATE category SET icon = 'pi-shopping-bag' WHERE owner_id IS NULL AND name = 'Shopping';
UPDATE category SET icon = 'pi-graduation-cap' WHERE owner_id IS NULL AND name = 'Education';
UPDATE category SET icon = 'pi-sync' WHERE owner_id IS NULL AND name = 'Subscriptions';
UPDATE category SET icon = 'pi-map' WHERE owner_id IS NULL AND name = 'Travel';
UPDATE category SET icon = 'pi-shield' WHERE owner_id IS NULL AND name = 'Insurance';
UPDATE category SET icon = 'pi-ellipsis-h' WHERE owner_id IS NULL AND name = 'Other Expense';
UPDATE category SET icon = 'pi-wallet' WHERE owner_id IS NULL AND name = 'Salary';
UPDATE category SET icon = 'pi-briefcase' WHERE owner_id IS NULL AND name = 'Freelance';
UPDATE category SET icon = 'pi-chart-line' WHERE owner_id IS NULL AND name = 'Investments';
UPDATE category SET icon = 'pi-gift' WHERE owner_id IS NULL AND name = 'Gifts';
UPDATE category SET icon = 'pi-ellipsis-h' WHERE owner_id IS NULL AND name = 'Other Income';
