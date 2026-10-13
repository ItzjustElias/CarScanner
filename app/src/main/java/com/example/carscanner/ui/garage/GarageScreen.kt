package com.example.carscanner.ui.garage

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.carscanner.CarScannerApp
import com.example.carscanner.data.CarEntity
import com.example.carscanner.data.CorrectionPreview
import com.example.carscanner.data.ProfileStats
import com.example.carscanner.domain.ScanException
import com.example.carscanner.domain.Levels
import com.example.carscanner.domain.Rarity
import com.example.carscanner.domain.SortMode
import com.example.carscanner.ui.components.CarDetailContent
import com.example.carscanner.ui.components.ConfirmDialog
import com.example.carscanner.ui.components.RarityChip
import com.example.carscanner.ui.components.RarityDot
import com.example.carscanner.ui.theme.Gold
import com.example.carscanner.ui.theme.rarityBrush
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat

private data class GarageParams(
    val userId: String,
    val query: String,
    val rarity: Rarity?,
    val sort: SortMode,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CarScannerApp).container.repository

    private val userId = MutableStateFlow("")
    private val _query = MutableStateFlow("")
    private val _rarity = MutableStateFlow<Rarity?>(null)
    private val _sort = MutableStateFlow(SortMode.NEWEST)

    val query: StateFlow<String> = _query.asStateFlow()
    val rarityFilter: StateFlow<Rarity?> = _rarity.asStateFlow()
    val sort: StateFlow<SortMode> = _sort.asStateFlow()
    val pagedCars: Flow<PagingData<CarEntity>> =
        combine(userId, _query.debounce { if (it.isEmpty()) 0L else 250L }, _rarity, _sort) { u, q, r, s ->
            GarageParams(u, q, r, s)
        }
            .distinctUntilChanged()
            .flatMapLatest { p ->
                if (p.userId.isBlank()) flowOf(PagingData.empty())
                else repo.pagedCars(p.userId, p.query, p.rarity, p.sort)
            }
            .cachedIn(viewModelScope)

    val stats: StateFlow<ProfileStats?> = userId
        .flatMapLatest { if (it.isBlank()) flowOf<ProfileStats?>(null) else repo.summary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _correction = MutableStateFlow<CorrectionState>(CorrectionState.Idle)
    val correction: StateFlow<CorrectionState> = _correction.asStateFlow()

    fun lookupCorrection(car: CarEntity, text: String) {
        if (_correction.value is CorrectionState.Loading) return
        _correction.value = CorrectionState.Loading
        viewModelScope.launch {
            _correction.value = try {
                CorrectionState.Preview(repo.lookupCorrection(car, text))
            } catch (e: ScanException) {
                CorrectionState.Error(e.message ?: "Lookup failed.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CorrectionState.Error("Something went wrong. Try again.")
            }
        }
    }

    fun applyCorrection(car: CarEntity, preview: CorrectionPreview, onDone: (CarEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = repo.applyCorrection(car, preview)
                _correction.value = CorrectionState.Idle
                onDone(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _correction.value = CorrectionState.Error("Couldn't save the change. Try again.")
            }
        }
    }

    fun resetCorrection() { _correction.value = CorrectionState.Idle }

    fun setUser(id: String) { userId.value = id }
    fun setQuery(value: String) { _query.value = value }
    fun setRarity(value: Rarity?) { _rarity.value = value }
    fun toggleSort() {
        _sort.value = if (_sort.value == SortMode.NEWEST) SortMode.TOP_POINTS else SortMode.NEWEST
    }
    fun delete(car: CarEntity) { viewModelScope.launch { repo.delete(car) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    user: FirebaseUser,
    onScan: () -> Unit,
    onProfile: () -> Unit,
    vm: GarageViewModel = viewModel(),
) {
    LaunchedEffect(user.uid) { vm.setUser(user.uid) }

    val cars = vm.pagedCars.collectAsLazyPagingItems()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val rarityFilter by vm.rarityFilter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<CarEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<CarEntity?>(null) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Rounded.CameraAlt, null) },
                text = { Text("Scan a car") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Header(user, stats, onProfile)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "controls") {
                Controls(
                    query = query,
                    onQuery = vm::setQuery,
                    rarity = rarityFilter,
                    onRarity = vm::setRarity,
                    sort = sort,
                    onSort = vm::toggleSort,
                    focusManager = focusManager,
                )
            }

            val garageEmpty = stats?.cars == 0
            when {
                garageEmpty -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyState() }
                cars.itemCount == 0 && cars.loadState.refresh !is LoadState.Loading && stats != null ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "nomatch") { NoMatches() }
                else -> items(count = cars.itemCount, key = cars.itemKey { it.id }) { index ->
                    cars[index]?.let { car -> CarCard(car) { selected = car } }
                }
            }

            if (cars.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }

    selected?.let { car ->
        var correcting by remember(car.id) { mutableStateOf(false) }
        val correction by vm.correction.collectAsStateWithLifecycle()
        ModalBottomSheet(
            onDismissRequest = { vm.resetCorrection(); selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (correcting) {
                    CorrectionContent(
                        car = car,
                        state = correction,
                        onSearch = { vm.lookupCorrection(car, it) },
                        onConfirm = { preview ->
                            vm.applyCorrection(car, preview) { updated ->
                                selected = updated
                                correcting = false
                            }
                        },
                        onCancel = { vm.resetCorrection(); correcting = false },
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityChip(car.rarityEnum)
                        Spacer(Modifier.weight(1f))
                        Text("+${car.points} pts", color = Gold, style = MaterialTheme.typography.titleLarge)
                    }
                    CarDetailContent(car)
                    TextButton(
                        onClick = { vm.resetCorrection(); correcting = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Not the right car? Fix it") }
                    OutlinedButton(onClick = { confirmDelete = car }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove from garage")
                    }
                }
            }
        }
    }

    confirmDelete?.let { car ->
        ConfirmDialog(
            title = "Remove ${car.title}?",
            message = "This deletes the car and takes its ${car.points} points away from your total. This can't be undone.",
            confirmText = "Remove",
            onConfirm = {
                vm.delete(car)
                confirmDelete = null
                selected = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun Header(user: FirebaseUser, stats: ProfileStats?, onProfile: () -> Unit) {
    val points = stats?.points ?: 0
    val level = Levels.forPoints(points)
    val rarest = stats?.rarityCounts?.keys?.maxOrNull()
    val number = NumberFormat.getIntegerInstance()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        user.displayName?.substringBefore(' ') ?: "Driver",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Level ${level.level}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onProfile) {
                Icon(Icons.Rounded.BarChart, "Profile and stats", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(Color(0xFF243056), Color(0xFF161A24))))
                .padding(20.dp),
        ) {
            Text("Total points", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(number.format(points), style = MaterialTheme.typography.displayLarge, color = Gold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { level.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Gold,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${number.format(level.pointsToNext)} pts to level ${level.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats != null && stats.cars > 0) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${stats.cars} cars", style = MaterialTheme.typography.titleMedium)
                    rarest?.let {
                        Text("Rarest:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RarityChip(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    query: String,
    onQuery: (String) -> Unit,
    rarity: Rarity?,
    onRarity: (Rarity?) -> Unit,
    sort: SortMode,
    onSort: () -> Unit,
    focusManager: FocusManager,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your garage") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RarityFilterChip(null, rarity == null) { onRarity(null) } }
            items(Rarity.entries.reversed()) { r ->
                RarityFilterChip(r, rarity == r) { onRarity(if (rarity == r) null else r) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sorted by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSort) { Text(sort.label) }
        }
    }
}

@Composable
private fun RarityFilterChip(rarity: Rarity?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(rarity?.label ?: "All") },
        leadingIcon = if (rarity != null) { { RarityDot(rarity) } } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text("Your garage is empty", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tap Scan a car and take a photo, or pick one from your gallery.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoMatches() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text("No cars match", style = MaterialTheme.typography.titleLarge)
        Text(
            "Try a different search or rarity filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CarCard(car: CarEntity, onClick: () -> Unit) {
    val rarity = car.rarityEnum
    val borderWidth = if (rarity >= Rarity.MYTHICAL) 2.5.dp else 1.5.dp
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(borderWidth, rarityBrush(rarity, alpha = 0.85f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.aspectRatio(0.85f)) {
            AsyncImage(
                model = File(car.imagePath),
                contentDescription = car.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0E1118)), startY = 150f)),
            )
            RarityChip(rarity, Modifier.align(Alignment.TopStart).padding(8.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(car.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("+${car.points} pts", style = MaterialTheme.typography.labelLarge, color = Gold)
            }
        }
    }
}
