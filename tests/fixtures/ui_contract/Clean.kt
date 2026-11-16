// Patterns tests/check_ui_contract.py allows; its self-test expects no problem here.
fun spacing() = Modifier.padding(horizontal = Afsh.spacing.lg, vertical = 0.dp)
fun gap() = Arrangement.spacedBy(Afsh.spacing.xs)
fun size() = Modifier.size(24.dp).heightIn(min = 48.dp)
fun shapes() = listOf(CircleShape, MaterialTheme.shapes.medium, RectangleShape)
fun color() = Afsh.colors.badge.container
fun icon() = Icon(AfshIcon.Search.painter(), null)
fun filled() = Icon(AfshIcon.Star.painter(filled = true), null)
