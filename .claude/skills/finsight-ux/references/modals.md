# Modal System

## Architecture

The project uses a custom modal system built on top of Material3's `ModalBottomSheet`.
Never instantiate `ModalBottomSheet { }` directly in a composable — always use the
project's `ModalBottomSheet` abstract class and `ModalManager`.

```
ModalManager (Koin single)
  └── holds List<Modal>
       └── renders via ModalManagerHost { }

Modal (abstract)
  └── ModalBottomSheet (abstract) : Modal, ViewModelStoreOwner
       └── Your concrete modal class
```

## Creating a New Modal

1. Create a class extending `ModalBottomSheet`
2. Implement `ColumnScope.BottomSheetContent()`
3. Inject ViewModel with `koinViewModel { parametersOf(...) }` inside the composable
4. Show via `LocalModalManager.current.show(MyModal(params))`

```kotlin
class MyFeatureModal(
    private val param: MyParam
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<MyFeatureViewModel> { parametersOf(param) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Modal Title",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )

            // Content...

            HorizontalDivider()

            // Primary action button
            Button(
                onClick = { viewModel.onSubmit() },
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Salvar",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
```

## Showing a Modal

```kotlin
// From a composable
val modalManager = LocalModalManager.current

Button(onClick = { modalManager.show(MyFeatureModal(param)) }) { ... }

// From a ViewModel Action (preferred for navigation-like flows)
// ViewModel sends Action → Screen collects → shows modal
LaunchedEffect(Unit) {
    viewModel.action.collect { action ->
        when (action) {
            is MyAction.ShowModal -> modalManager.show(MyFeatureModal(action.param))
        }
    }
}
```

## Dismissing a Modal

```kotlin
// From within the modal's BottomSheetContent
val manager = LocalModalManager.current
manager.dismiss(this@MyFeatureModal) // dismiss self

// From ViewModel via Action
// ViewModel → sends Action.Dismiss → modal observes → calls manager.dismiss()
```

## Standard Modal Layouts

### Confirmation Modal (delete/destructive)
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp)
) {
    Text(
        text = stringResource(Res.string.delete_title),
        style = MaterialTheme.typography.headlineSmall,
        color = colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(Res.string.delete_message),
        style = MaterialTheme.typography.bodyLarge,
        color = colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { viewModel.onConfirm() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
    ) {
        Text(
            text = stringResource(Res.string.delete_confirm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}
```

### Form Modal (create/edit)
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    // Title: headlineSmall or titleLarge (20sp SemiBold)
    Text(text = title, style = MaterialTheme.typography.headlineSmall)

    // Fields (OutlinedTextField with RoundedCornerShape(12.dp))
    OutlinedTextField(
        state = nameState,
        label = { Text(text = stringResource(Res.string.field_label)) },
        shape = RoundedCornerShape(12.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.animateContentSize().fillMaxWidth(),
    )

    HorizontalDivider()

    // Submit button
    Button(
        onClick = { viewModel.onSubmit() },
        enabled = uiState.canSubmit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = stringResource(Res.string.save), fontWeight = FontWeight.Bold)
    }
}
```

## Rules

- `ModalBottomSheet` always gets its own `ViewModelStore` — ViewModels live only while the modal is shown.
- `viewModelStore.clear()` is called automatically in `onDismissed()` — don't override without calling `super`.
- Modal content should always have `verticalScroll` for form modals — keyboard may push content up.
- Bottom padding is always `32.dp` — ensures content is not hidden behind the system nav bar.
- `skipPartiallyExpanded = true` is the default — modals always open fully expanded.
