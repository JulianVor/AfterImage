param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'

function Normalize-Title([string] $Title) {
    if ([string]::IsNullOrWhiteSpace($Title)) { return '' }
    $normalized = ($Title -replace '_', ' ').Trim()
    if ($normalized.Contains('#')) {
        $normalized = $normalized.Substring(0, $normalized.IndexOf('#')).Trim()
    }
    if ($normalized.Length -gt 0) {
        $normalized = $normalized.Substring(0, 1).ToUpperInvariant() + $normalized.Substring(1)
    }
    return $normalized
}

function Add-Count([hashtable] $Map, [string] $Key, [int] $Increment = 1) {
    if ([string]::IsNullOrWhiteSpace($Key)) { return }
    if ($Map.ContainsKey($Key)) { $Map[$Key] += $Increment } else { $Map[$Key] = $Increment }
}

function Parse-SetBlocks([string] $Text) {
    $blocks = [System.Collections.Generic.List[string]]::new()
    $needle = '{{#set:'
    $offset = 0
    while (($start = $Text.IndexOf($needle, $offset, [StringComparison]::OrdinalIgnoreCase)) -ge 0) {
        $depth = 0
        $end = -1
        for ($i = $start; $i -lt ($Text.Length - 1); $i++) {
            $pair = $Text.Substring($i, 2)
            if ($pair -eq '{{') { $depth++; $i++; continue }
            if ($pair -eq '}}') {
                $depth--; $i++
                if ($depth -eq 0) { $end = $i + 1; break }
            }
        }
        if ($end -lt 0) { break }
        $blocks.Add($Text.Substring($start + $needle.Length, $end - ($start + $needle.Length) - 2))
        $offset = $end
    }
    return $blocks
}

function Parse-TemplateBlocks([string] $Text, [string] $Needle) {
    $blocks = [System.Collections.Generic.List[object]]::new()
    $offset = 0
    while (($start = $Text.IndexOf($Needle, $offset, [StringComparison]::OrdinalIgnoreCase)) -ge 0) {
        $depth = 0
        $end = -1
        for ($i = $start; $i -lt ($Text.Length - 1); $i++) {
            $pair = $Text.Substring($i, 2)
            if ($pair -eq '{{') { $depth++; $i++; continue }
            if ($pair -eq '}}') {
                $depth--; $i++
                if ($depth -eq 0) { $end = $i + 1; break }
            }
        }
        if ($end -lt 0) { break }
        $blocks.Add([ordered] @{ start = $start; length = $end - $start; text = $Text.Substring($start, $end - $start) })
        $offset = $end
    }
    return $blocks
}

function Remove-TemplateBlocks([string] $Text, [string] $Needle) {
    $blocks = @(Parse-TemplateBlocks $Text $Needle)
    for ($i = $blocks.Count - 1; $i -ge 0; $i--) {
        $block = $blocks[$i]
        $Text = $Text.Remove($block.start, $block.length).Insert($block.start, (' ' * $block.length))
    }
    return $Text
}

function Remove-NonExecutableMarkup([string] $Text) {
    $options = [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::Singleline
    $Text = [regex]::Replace($Text, '<!--.*?-->', ' ', $options)
    $Text = [regex]::Replace($Text, '<nowiki\b[^>]*>.*?</nowiki\s*>', ' ', $options)
    $Text = [regex]::Replace($Text, '<(?:pre|source|syntaxhighlight)\b[^>]*>.*?</(?:pre|source|syntaxhighlight)\s*>', ' ', $options)
    return $Text
}

function Split-Pipes([string] $Value) {
    $parts = [System.Collections.Generic.List[string]]::new()
    $start = 0
    $templateDepth = 0
    $linkDepth = 0
    for ($i = 0; $i -lt $Value.Length; $i++) {
        if ($i -lt $Value.Length - 1) {
            $pair = $Value.Substring($i, 2)
            if ($pair -eq '{{') { $templateDepth++; $i++; continue }
            if ($pair -eq '}}') { if ($templateDepth -gt 0) { $templateDepth-- }; $i++; continue }
            if ($pair -eq '[[') { $linkDepth++; $i++; continue }
            if ($pair -eq ']]') { if ($linkDepth -gt 0) { $linkDepth-- }; $i++; continue }
        }
        if ($Value[$i] -eq '|' -and $templateDepth -eq 0 -and $linkDepth -eq 0) {
            $parts.Add($Value.Substring($start, $i - $start))
            $start = $i + 1
        }
    }
    $parts.Add($Value.Substring($start))
    return $parts
}

[xml] $document = Get-Content -Raw -LiteralPath $InputPath
$namespace = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
$namespace.AddNamespace('mw', $document.DocumentElement.NamespaceURI)

$siteName = $document.SelectSingleNode('/mw:mediawiki/mw:siteinfo/mw:sitename', $namespace).InnerText
$baseUrl = $document.SelectSingleNode('/mw:mediawiki/mw:siteinfo/mw:base', $namespace).InnerText
$generator = $document.SelectSingleNode('/mw:mediawiki/mw:siteinfo/mw:generator', $namespace).InnerText
$namespaceNames = @{}
$namespaceNamesForJson = [ordered] @{}
foreach ($node in $document.SelectNodes('/mw:mediawiki/mw:siteinfo/mw:namespaces/mw:namespace', $namespace)) {
    $namespaceNames[[int] $node.GetAttribute('key')] = $node.InnerText
    $namespaceNamesForJson[[string] $node.GetAttribute('key')] = $node.InnerText
}

$pages = [System.Collections.Generic.List[object]]::new()
$namespaceCounts = @{}
$categoryCounts = @{}
$templateCounts = @{}
$propertyCounts = @{}
$propertyPageCounts = @{}
$propertyVariants = @{}
$propertyValueSamples = @{}
$queryPropertyCounts = @{}
$pageTitleLookup = @{}
$linkTargets = @{}
$externalHostCounts = @{}
$pageTypeCounts = @{}
$modelCounts = @{}
$formatCounts = @{}
$totalRevisionCount = 0

$pageNodes = $document.SelectNodes('/mw:mediawiki/mw:page', $namespace)
foreach ($pageNode in $pageNodes) {
    $title = $pageNode.SelectSingleNode('mw:title', $namespace).InnerText
    $normalizedTitle = Normalize-Title $title
    $pageTitleLookup[$normalizedTitle.ToLowerInvariant()] = $true
    $pageId = [long] $pageNode.SelectSingleNode('mw:id', $namespace).InnerText
    $namespaceId = [int] $pageNode.SelectSingleNode('mw:ns', $namespace).InnerText
    Add-Count $namespaceCounts ([string] $namespaceId)

    $revisionNodes = @($pageNode.SelectNodes('mw:revision', $namespace))
    $totalRevisionCount += $revisionNodes.Count
    $latestRevision = $revisionNodes | Sort-Object { [datetime] $_.SelectSingleNode('mw:timestamp', $namespace).InnerText } -Descending | Select-Object -First 1
    if ($null -eq $latestRevision) { continue }

    $revisionId = [long] $latestRevision.SelectSingleNode('mw:id', $namespace).InnerText
    $timestamp = $latestRevision.SelectSingleNode('mw:timestamp', $namespace).InnerText
    $modelNode = $latestRevision.SelectSingleNode('mw:model', $namespace)
    $formatNode = $latestRevision.SelectSingleNode('mw:format', $namespace)
    $model = if ($modelNode) { $modelNode.InnerText } else { '' }
    $format = if ($formatNode) { $formatNode.InnerText } else { '' }
    Add-Count $modelCounts $model
    Add-Count $formatCounts $format
    $textNode = $latestRevision.SelectSingleNode('mw:text', $namespace)
    $text = if ($textNode) { $textNode.InnerText } else { '' }

    $executableText = Remove-NonExecutableMarkup $text

    $categories = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($executableText, '\[\[\s*(?:Category|Kategorie)\s*:\s*([^\]|#]+)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
        $category = $match.Groups[1].Value.Trim()
        if (-not $categories.Contains($category)) { $categories.Add($category) }
        Add-Count $categoryCounts $category
    }

    $templates = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($executableText, '\{\{\s*([^#\{\}\|\r\n]+)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
        $template = $match.Groups[1].Value.Trim()
        if ($template -and -not $templates.Contains($template)) { $templates.Add($template) }
        Add-Count $templateCounts $template
    }

    $properties = [ordered] @{}
    $propertyKeysOnPage = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($block in (Parse-SetBlocks $executableText)) {
        foreach ($segment in (Split-Pipes $block)) {
            $equalsAt = $segment.IndexOf('=')
            if ($equalsAt -le 0) { continue }
            $propertyRaw = $segment.Substring(0, $equalsAt).Trim()
            $property = ($propertyRaw -replace '\s+', ' ').Trim()
            $value = $segment.Substring($equalsAt + 1).Trim()
            if ([string]::IsNullOrWhiteSpace($property)) { continue }
            if (-not $properties.Contains($property)) { $properties[$property] = [System.Collections.Generic.List[string]]::new() }
            $properties[$property].Add($value)
            Add-Count $propertyCounts $property
            [void] $propertyKeysOnPage.Add($property)
            $canonical = $property.ToLowerInvariant()
            if (-not $propertyVariants.ContainsKey($canonical)) { $propertyVariants[$canonical] = @{} }
            Add-Count $propertyVariants[$canonical] $property
            if (-not $propertyValueSamples.ContainsKey($property)) { $propertyValueSamples[$property] = [System.Collections.Generic.List[string]]::new() }
            if ($propertyValueSamples[$property].Count -lt 20 -and -not $propertyValueSamples[$property].Contains($value)) {
                $propertyValueSamples[$property].Add($value)
            }
        }
    }
    foreach ($askBlock in (Parse-TemplateBlocks $executableText '{{#ask:')) {
        foreach ($match in [regex]::Matches($askBlock.text, '\[\[\s*([^\[\]\|:]+?)\s*::')) {
            Add-Count $queryPropertyCounts (($match.Groups[1].Value -replace '\s+', ' ').Trim())
        }
        foreach ($match in [regex]::Matches($askBlock.text, '\|\s*\?\s*([^=\|\r\n\}]+)')) {
            Add-Count $queryPropertyCounts (($match.Groups[1].Value -replace '\s+', ' ').Trim())
        }
    }
    $factText = Remove-TemplateBlocks $executableText '{{#ask:'
    foreach ($match in [regex]::Matches($factText, '\[\[\s*([^\[\]\|:]+?)\s*::\s*([^\]]+)\]\]')) {
        $propertyRaw = $match.Groups[1].Value.Trim()
        $property = ($propertyRaw -replace '\s+', ' ').Trim()
        $value = $match.Groups[2].Value.Trim()
        if (-not $properties.Contains($property)) { $properties[$property] = [System.Collections.Generic.List[string]]::new() }
        $properties[$property].Add($value)
        Add-Count $propertyCounts $property
        [void] $propertyKeysOnPage.Add($property)
        $canonical = $property.ToLowerInvariant()
        if (-not $propertyVariants.ContainsKey($canonical)) { $propertyVariants[$canonical] = @{} }
        Add-Count $propertyVariants[$canonical] $property
        if (-not $propertyValueSamples.ContainsKey($property)) { $propertyValueSamples[$property] = [System.Collections.Generic.List[string]]::new() }
        if ($propertyValueSamples[$property].Count -lt 20 -and -not $propertyValueSamples[$property].Contains($value)) {
            $propertyValueSamples[$property].Add($value)
        }
    }
    foreach ($property in $propertyKeysOnPage) { Add-Count $propertyPageCounts $property }

    $links = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($executableText, '\[\[\s*([^\[\]\|]+?)(?:\|[^\]]*)?\]\]')) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '::') { continue }
        if ($target -match '^(?i)(Category|Kategorie|File|Datei|Image|Media):') { continue }
        $normalizedTarget = Normalize-Title $target
        if ($normalizedTarget -and -not $links.Contains($normalizedTarget)) { $links.Add($normalizedTarget) }
        if ($normalizedTarget) { Add-Count $linkTargets $normalizedTarget }
    }

    $externalUrls = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($executableText, 'https?://[^\s\]\}\|<>"'']+')) {
        $url = $match.Value.TrimEnd('.', ',', ';', ')')
        if (-not $externalUrls.Contains($url)) { $externalUrls.Add($url) }
        try { Add-Count $externalHostCounts ([uri] $url).Host.ToLowerInvariant() } catch { }
    }

    $displayTitleMatch = [regex]::Match($executableText, '\{\{\s*DISPLAYTITLE\s*:\s*(.*?)\}\}', [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::Singleline)
    $displayTitle = if ($displayTitleMatch.Success) { $displayTitleMatch.Groups[1].Value.Trim() } else { $null }
    $redirectMatch = [regex]::Match($executableText, '^\s*#(?:REDIRECT|WEITERLEITUNG)\s*\[\[([^\]]+)\]\]', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $redirectTarget = if ($redirectMatch.Success) { Normalize-Title $redirectMatch.Groups[1].Value } else { $null }

    $pageType = 'CONTENT'
    if ($namespaceId -eq 10) { $pageType = 'TEMPLATE' }
    elseif ($namespaceId -eq 14) { $pageType = 'CATEGORY' }
    elseif ($namespaceId -eq 6) { $pageType = 'FILE' }
    elseif ($namespaceId -ne 0) { $pageType = 'OTHER_NAMESPACE' }
    elseif ($redirectTarget) { $pageType = 'REDIRECT' }
    elseif ($title -match '(?i)(^|/)Privat($|/)|^Privat:') { $pageType = 'PRIVATE_NAMED' }
    Add-Count $pageTypeCounts $pageType

    $plainLength = ([regex]::Replace($text, '[\{\}\[\]\|=''"]', '')).Length
    $pages.Add([ordered] @{
        title = $title
        normalizedTitle = $normalizedTitle
        namespaceId = $namespaceId
        namespaceName = $namespaceNames[$namespaceId]
        pageId = $pageId
        revisionCount = $revisionNodes.Count
        revisionId = $revisionId
        timestamp = $timestamp
        model = $model
        format = $format
        pageType = $pageType
        isPrivateNamed = [bool] ($title -match '(?i)(^|/)Privat($|/)|^Privat:')
        redirectTarget = $redirectTarget
        displayTitle = $displayTitle
        categories = @($categories)
        templates = @($templates)
        properties = $properties
        links = @($links)
        externalUrls = @($externalUrls)
        textLength = $text.Length
        approximatePlainTextLength = $plainLength
    })
}

$brokenLinks = [System.Collections.Generic.List[object]]::new()
foreach ($page in $pages) {
    foreach ($target in $page.links) {
        if (-not $pageTitleLookup.ContainsKey($target.ToLowerInvariant())) {
            $brokenLinks.Add([ordered] @{ source = $page.title; target = $target })
        }
    }
}

$duplicates = [System.Collections.Generic.List[object]]::new()
$titleGroups = $pages | Group-Object { (($_.normalizedTitle -replace '[^\p{L}\p{Nd}]', '').ToLowerInvariant()) } | Where-Object { $_.Count -gt 1 }
foreach ($group in $titleGroups) {
    $duplicates.Add([ordered] @{ normalizedKey = $group.Name; titles = @($group.Group.title) })
}

$result = [ordered] @{
    generatedAt = [datetime]::UtcNow.ToString('o')
    inputPath = (Resolve-Path -LiteralPath $InputPath).Path
    site = [ordered] @{ name = $siteName; baseUrl = $baseUrl; generator = $generator; namespaces = $namespaceNamesForJson }
    totals = [ordered] @{ pages = $pages.Count; revisions = $totalRevisionCount; brokenLinks = $brokenLinks.Count; possibleDuplicateGroups = $duplicates.Count }
    counts = [ordered] @{
        namespaces = $namespaceCounts
        pageTypes = $pageTypeCounts
        models = $modelCounts
        formats = $formatCounts
        categories = $categoryCounts
        templates = $templateCounts
        properties = $propertyCounts
        propertyPages = $propertyPageCounts
        queryProperties = $queryPropertyCounts
        externalHosts = $externalHostCounts
        linkTargets = $linkTargets
    }
    propertyVariants = $propertyVariants
    propertyValueSamples = $propertyValueSamples
    pages = $pages
    brokenLinks = $brokenLinks
    possibleDuplicates = $duplicates
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $OutputPath -Encoding utf8

Write-Output "Audited $($pages.Count) pages and $totalRevisionCount revisions."
Write-Output "Properties: $($propertyCounts.Count); categories: $($categoryCounts.Count); broken links: $($brokenLinks.Count)."
Write-Output "Report: $OutputPath"
